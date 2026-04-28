package esprit.subscription.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import esprit.subscription.DTO.unlockchat.UnlockChatMessageDTO;
import esprit.subscription.config.LivenessAdminAuthHelper;
import esprit.subscription.entity.AdminNotification;
import esprit.subscription.entity.AiDecision;
import esprit.subscription.entity.ChatSessionStatus;
import esprit.subscription.entity.MessageSender;
import esprit.subscription.entity.UnlockChatMessage;
import esprit.subscription.entity.UnlockChatSession;
import esprit.subscription.repository.UnlockChatMessageRepository;
import esprit.subscription.repository.UnlockChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Runs Groq-based fraud analysis after user messages in unlock-chat.
 */
@Service
public class UnlockChatAiProcessor {

    private static final Logger log = LoggerFactory.getLogger(UnlockChatAiProcessor.class);

    private static final String SYSTEM_PROMPT = """
            You are a fraud detection agent for a payment verification system.
            Analyze this conversation between a user and support.
            The user failed an automated liveness camera test and is requesting manual access.

            Evaluate based on:
            - Coherence and consistency of answers
            - Natural human conversational patterns
            - Ability to answer context-specific questions
            - Signs of scripted/bot responses (too fast, repetitive, unnatural)
            - Emotional context (frustration, confusion = human signals)
            - Short polite exchanges alone are NOT enough to label BOT; prefer UNCERTAIN or lower confidence unless evidence is strong.

            Confidence must reflect evidence: use the full 0-100 range. Do NOT default to round numbers like 80, 75, or 50 unless the transcript supports them.
            If the conversation is too short to tell, use UNCERTAIN with confidence under 60.

            Respond ONLY with valid JSON:
            {
              "decision": "HUMAN" | "BOT" | "UNCERTAIN",
              "confidence": 0-100,
              "reasoning": "one sentence explanation",
              "recommendUnblock": true | false
            }

            Only set recommendUnblock=true if decision=HUMAN and confidence >= 75.
            """;

    private static final String MSG_AI_UNBLOCK = """
            ✅ Notre agent IA a analysé votre conversation et vous a identifié comme humain. Votre accès au paiement a été restauré automatiquement.""";

    private static final String MSG_AI_ESCALATE = """
            🚫 Notre agent IA n'a pas pu confirmer votre identité. Un administrateur humain va examiner votre cas.""";

    private final GroqChatClient groqChatClient;
    private final UnlockChatSessionRepository sessionRepository;
    private final UnlockChatMessageRepository messageRepository;
    private final UserBlockService userBlockService;
    private final SseStreamService sseStreamService;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final AdminNotificationService adminNotificationService;
    private final LivenessAdminAuthHelper livenessAdminAuthHelper;

    @Value("${liveness.admin.email:}")
    private String livenessAdminEmail;

    /** Si false (défaut) : pas d’e-mail — jeton + SSE + cloche uniquement. */
    @Value("${liveness.admin.email.human-review.enabled:false}")
    private boolean humanReviewEmailEnabled;

    @Value("${liveness.admin.notification.enabled:true}")
    private boolean livenessAdminNotificationEnabled;

    /** Min USER messages before escalade NEEDS_HUMAN_REVIEW + notification (was 10: too high for short chats). */
    @Value("${skillio.unlock-chat.ai.escalate-min-user-messages:3}")
    private int escalateMinUserMessages;

    @Value("${skillio.unlock-chat.groq.temperature:0.42}")
    private double groqTemperature;

    @Value("${skillio.unlock-chat.groq.forced-temperature:0.78}")
    private double groqForcedTemperature;

    public UnlockChatAiProcessor(
            GroqChatClient groqChatClient,
            UnlockChatSessionRepository sessionRepository,
            UnlockChatMessageRepository messageRepository,
            UserBlockService userBlockService,
            SseStreamService sseStreamService,
            ObjectMapper objectMapper,
            EmailService emailService,
            AdminNotificationService adminNotificationService,
            LivenessAdminAuthHelper livenessAdminAuthHelper) {
        this.groqChatClient = groqChatClient;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.userBlockService = userBlockService;
        this.sseStreamService = sseStreamService;
        this.objectMapper = objectMapper;
        this.emailService = emailService;
        this.adminNotificationService = adminNotificationService;
        this.livenessAdminAuthHelper = livenessAdminAuthHelper;
    }

    @Transactional
    public void process(Long sessionId, boolean adminForcedReanalysis) {
        UnlockChatSession session = sessionRepository.findByIdWithMessages(sessionId).orElse(null);
        if (session == null) {
            return;
        }
        ChatSessionStatus st = session.getStatus();
        ChatSessionStatus statusBefore = st;
        if (st != ChatSessionStatus.OPEN && st != ChatSessionStatus.NEEDS_HUMAN_REVIEW) {
            return;
        }

        List<UnlockChatMessage> all = messageRepository.findBySessionOrderBySentAtAsc(session);
        int from = Math.max(0, all.size() - 10);
        List<UnlockChatMessage> last = all.subList(from, all.size());
        String transcript = last.stream()
                .map(m -> label(m.getSender()) + ": " + m.getContent())
                .collect(Collectors.joining("\n"));
        if (adminForcedReanalysis) {
            transcript = transcript
                    + "\n\n---\n[Admin re-analysis run "
                    + Instant.now().toEpochMilli()
                    + "] Independent scoring pass — set confidence from current transcript only; it may legitimately differ from any prior run.\n";
        }
        transcript = transcript + "\n[nonce=" + UUID.randomUUID() + "]\n";

        double temp = adminForcedReanalysis ? groqForcedTemperature : groqTemperature;
        Optional<String> rawJson = groqChatClient.completeJson(SYSTEM_PROMPT, transcript, temp);
        if (rawJson.isEmpty()) {
            log.warn(
                    "Groq returned no response for session {} — check groq.api.enabled, groq.api.key / GROQ_API_KEY, network.",
                    sessionId);
            persistAnalysisFailure(
                    session,
                    AiDecision.UNCERTAIN,
                    "Groq indisponible : clé API absente, désactivée, ou erreur réseau. "
                            + "Configurez GROQ_API_KEY (ou groq.api.key) et redémarrez le service subscription.");
            return;
        }

        JsonNode node;
        try {
            node = parseAiJson(rawJson.get());
        } catch (Exception e) {
            log.warn("Could not parse AI JSON for session {}: {}", sessionId, e.getMessage());
            persistAnalysisFailure(
                    session,
                    AiDecision.UNCERTAIN,
                    "Réponse IA non JSON ou illisible : " + e.getMessage());
            return;
        }

        AiDecision decision = parseDecision(node.path("decision").asText(null));
        int confidence = parseConfidence(node);
        String reasoning = node.path("reasoning").asText("");
        boolean recommendUnblock = node.path("recommendUnblock").asBoolean(false);

        session.setAiDecision(decision);
        session.setAiConfidence(confidence >= 0 ? confidence : null);
        session.setAiReasoning(reasoning);
        session.setAiRecommendUnblock(recommendUnblock);
        session.setAiAnalyzedAt(LocalDateTime.now());

        long userMsgCount = messageRepository.countBySessionAndSender(session, MessageSender.USER);
        long adminMsgCount = messageRepository.countBySessionAndSender(session, MessageSender.ADMIN);
        boolean enoughForEscalation = userMsgCount >= escalateMinUserMessages
                || (userMsgCount >= 1 && adminMsgCount >= 1);

        boolean humanUnlock = recommendUnblock
                && decision == AiDecision.HUMAN
                && confidence >= 75;

        if (humanUnlock) {
            session.setStatus(ChatSessionStatus.AI_RESOLVED);
            session.setClosedAt(LocalDateTime.now());
            session.setClosedBy("unlock-ai");
            userBlockService.approveUnlock(session.getBlockId(), "unlock-ai");
            UnlockChatMessage sys = appendSystemMessage(session, MSG_AI_UNBLOCK.trim());
            sessionRepository.saveAndFlush(session);
            sseStreamService.pushToUser(session.getUserId(), "new-message", toDto(sys, session.getId()));
            sseStreamService.pushToUser(session.getUserId(), "unlock-decision", Map.of("decision", "ai_granted"));
            sseStreamService.pushToAdmin("session-updated", Map.of("sessionId", session.getId(), "status", "AI_RESOLVED"));
            return;
        }

        if (enoughForEscalation && decision == AiDecision.BOT && !recommendUnblock) {
            if (statusBefore == ChatSessionStatus.OPEN) {
                session.setStatus(ChatSessionStatus.NEEDS_HUMAN_REVIEW);
                UnlockChatMessage sys = appendSystemMessage(session, MSG_AI_ESCALATE.trim());
                sessionRepository.saveAndFlush(session);
                long totalMessages = messageRepository.countBySession(session);
                sseStreamService.pushToUser(session.getUserId(), "new-message", toDto(sys, session.getId()));
                sseStreamService.pushToAdmin("session-updated", Map.of("sessionId", session.getId(), "status", "NEEDS_HUMAN_REVIEW"));
                notifyAdminsHumanReviewRequired(session, totalMessages);
            } else {
                sessionRepository.save(session);
                sseStreamService.pushToAdmin("session-updated", Map.of("sessionId", session.getId(), "aiAnalyzed", true));
            }
            return;
        }

        sessionRepository.save(session);
        sseStreamService.pushToAdmin("session-updated", Map.of("sessionId", session.getId(), "aiAnalyzed", true));
    }

    /**
     * Email + SSE + persistance admin — ne doit jamais bloquer le flux unlock-chat.
     */
    private void notifyAdminsHumanReviewRequired(UnlockChatSession session, long messageCount) {
        if (!livenessAdminNotificationEnabled) {
            return;
        }
        AdminNotification saved = null;
        try {
            saved = adminNotificationService.createNotification(
                    "HUMAN_REVIEW_ALERT",
                    session.getId(),
                    session.getUserId(),
                    "Intervention requise — révision humaine (session unlock-chat).",
                    session.getAiDecision() != null ? session.getAiDecision().name() : null,
                    session.getAiConfidence());
        } catch (Exception e) {
            log.warn("Admin notification DB failed for session {}: {}", session.getId(), e.getMessage());
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "HUMAN_REVIEW_ALERT");
            if (saved != null) {
                payload.put("notificationId", saved.getId());
                payload.put("actionToken", saved.getActionToken());
            }
            payload.put("sessionId", session.getId());
            payload.put("userId", parseUserIdJson(session.getUserId()));
            payload.put(
                    "aiDecision",
                    session.getAiDecision() != null ? session.getAiDecision().name() : "UNCERTAIN");
            payload.put("aiConfidence", session.getAiConfidence() != null ? session.getAiConfidence() : 0);
            payload.put("aiReasoning", session.getAiReasoning() != null ? session.getAiReasoning() : "");
            payload.put(
                    "timestamp",
                    Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            sseStreamService.sendToAdmins("HUMAN_REVIEW_ALERT", payload);
            // Même événement sur le flux unlock-chat (/admin/stream) : la cloche utilise un autre SSE souvent non connecté.
            sseStreamService.pushToAdmin("HUMAN_REVIEW_ALERT", payload);
        } catch (Exception e) {
            log.warn("Admin notification SSE failed for session {}: {}", session.getId(), e.getMessage());
        }
        if (humanReviewEmailEnabled
                && livenessAdminEmail != null
                && !livenessAdminEmail.isBlank()) {
            try {
                emailService.sendHumanReviewAlertEmail(
                        livenessAdminEmail,
                        session,
                        messageCount,
                        livenessAdminAuthHelper.getCurrentCode());
            } catch (Exception e) {
                log.warn("Human review alert email failed for session {}: {}", session.getId(), e.getMessage());
            }
        }
    }

    private static Object parseUserIdJson(String userId) {
        if (userId == null) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            return userId;
        }
    }

    /** Persist a row so the admin UI shows why analysis did not complete normally. */
    private void persistAnalysisFailure(UnlockChatSession session, AiDecision decision, String reasoning) {
        session.setAiDecision(decision);
        session.setAiConfidence(null);
        session.setAiReasoning(reasoning);
        session.setAiRecommendUnblock(false);
        session.setAiAnalyzedAt(LocalDateTime.now());
        sessionRepository.save(session);
        sseStreamService.pushToAdmin(
                "session-updated", Map.of("sessionId", session.getId(), "aiAnalyzed", true, "aiFailed", true));
    }

    private static UnlockChatMessage appendSystemMessage(UnlockChatSession session, String content) {
        LocalDateTime now = LocalDateTime.now();
        UnlockChatMessage m = UnlockChatMessage.builder()
                .session(session)
                .sender(MessageSender.SYSTEM)
                .content(content)
                .sentAt(now)
                .build();
        session.getMessages().add(m);
        return m;
    }

    private UnlockChatMessageDTO toDto(UnlockChatMessage m, Long sessionId) {
        return UnlockChatMessageDTO.builder()
                .id(m.getId())
                .sessionId(sessionId)
                .sender(m.getSender())
                .content(m.getContent())
                .sentAt(m.getSentAt())
                .build();
    }

    private static String label(MessageSender s) {
        if (s == MessageSender.ADMIN) {
            return "ADMIN";
        }
        if (s == MessageSender.SYSTEM) {
            return "SYSTEM";
        }
        return "USER";
    }

    private JsonNode parseAiJson(String raw) throws Exception {
        String t = raw.trim();
        if (t.startsWith("```")) {
            int start = t.indexOf('{');
            int end = t.lastIndexOf('}');
            if (start >= 0 && end > start) {
                t = t.substring(start, end + 1);
            }
        }
        return objectMapper.readTree(t);
    }

    private static AiDecision parseDecision(String s) {
        if (s == null) {
            return AiDecision.UNCERTAIN;
        }
        String u = s.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "HUMAN" -> AiDecision.HUMAN;
            case "BOT" -> AiDecision.BOT;
            default -> AiDecision.UNCERTAIN;
        };
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) {
            return min;
        }
        return Math.min(v, max);
    }

    /**
     * Groq may return confidence as int, double, or string; {@link JsonNode#asInt} on a text node yields 0.
     */
    private static int parseConfidence(JsonNode root) {
        JsonNode c = root.path("confidence");
        if (c.isMissingNode() || c.isNull()) {
            return -1;
        }
        try {
            if (c.isNumber()) {
                double d = c.asDouble();
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    return -1;
                }
                return clamp((int) Math.round(d), 0, 100);
            }
            if (c.isTextual()) {
                String t = c.asText().trim();
                if (t.isEmpty()) {
                    return -1;
                }
                double d = Double.parseDouble(t.replace(',', '.'));
                return clamp((int) Math.round(d), 0, 100);
            }
        } catch (Exception ignored) {
            return -1;
        }
        return -1;
    }
}
