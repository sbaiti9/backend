package esprit.subscription.service;

import esprit.subscription.DTO.unlockchat.UnlockChatMessageDTO;
import esprit.subscription.DTO.unlockchat.UnlockChatSessionDTO;
import esprit.subscription.DTO.unlockchat.UnlockChatSessionSummaryDTO;
import esprit.subscription.entity.BlockStatus;
import esprit.subscription.entity.ChatSessionStatus;
import esprit.subscription.entity.MessageSender;
import esprit.subscription.entity.UnlockChatMessage;
import esprit.subscription.entity.UnlockChatSession;
import esprit.subscription.entity.UserBlock;
import esprit.subscription.repository.UnlockChatMessageRepository;
import esprit.subscription.repository.UnlockChatSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UnlockChatService {

    private static String normalizeUserId(String userId) {
        return userId == null ? "" : userId.trim();
    }

    private final UnlockChatSessionRepository sessionRepository;
    private final UnlockChatMessageRepository messageRepository;
    private final UserBlockService userBlockService;
    private final SseStreamService sseStreamService;
    private final UnlockChatAiAnalysisService unlockChatAiAnalysisService;

    public UnlockChatService(
            UnlockChatSessionRepository sessionRepository,
            UnlockChatMessageRepository messageRepository,
            UserBlockService userBlockService,
            SseStreamService sseStreamService,
            UnlockChatAiAnalysisService unlockChatAiAnalysisService) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.userBlockService = userBlockService;
        this.sseStreamService = sseStreamService;
        this.unlockChatAiAnalysisService = unlockChatAiAnalysisService;
    }

    @Transactional
    public UnlockChatSession openSession(String userId, Long blockId, String firstMessage) {
        userId = normalizeUserId(userId);
        if (userId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId required");
        }
        Optional<UnlockChatSession> alreadyOpen = sessionRepository.findByUserIdAndStatus(userId, ChatSessionStatus.OPEN);
        if (alreadyOpen.isPresent()) {
            return sessionRepository.findByIdWithMessages(alreadyOpen.get().getId())
                    .orElse(alreadyOpen.get());
        }
        Optional<UnlockChatSession> inHumanReview = sessionRepository.findByUserIdAndStatus(
                userId, ChatSessionStatus.NEEDS_HUMAN_REVIEW);
        if (inHumanReview.isPresent()) {
            return sessionRepository.findByIdWithMessages(inHumanReview.get().getId())
                    .orElse(inHumanReview.get());
        }
        if (firstMessage == null || firstMessage.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message required");
        }
        UserBlock block = userBlockService.getByIdOrThrow(blockId);
        if (!block.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "blockId ne correspond pas à l'utilisateur");
        }
        if (block.getStatus() == BlockStatus.UNLOCKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Compte déjà débloqué");
        }

        LocalDateTime now = LocalDateTime.now();
        UnlockChatSession session = UnlockChatSession.builder()
                .userId(userId)
                .blockId(blockId)
                .status(ChatSessionStatus.OPEN)
                .createdAt(now)
                .build();

        UnlockChatMessage first = UnlockChatMessage.builder()
                .session(session)
                .sender(MessageSender.USER)
                .content(firstMessage.trim())
                .sentAt(now)
                .build();
        session.getMessages().add(first);

        session = sessionRepository.save(session);

        sseStreamService.pushToAdmin("new-session", Map.of(
                "sessionId", session.getId(),
                "userId", userId,
                "preview", preview(firstMessage, 80)));
        unlockChatAiAnalysisService.scheduleAnalysis(session.getId());
        return session;
    }

    @Transactional
    public UnlockChatMessage userSendMessage(Long sessionId, String userId, String content) {
        userId = normalizeUserId(userId);
        if (userId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId required");
        }
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content required");
        }
        UnlockChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session introuvable"));
        if (!session.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session invalide");
        }
        if (!isSessionActiveForChat(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session fermée");
        }

        LocalDateTime now = LocalDateTime.now();
        UnlockChatMessage m = UnlockChatMessage.builder()
                .session(session)
                .sender(MessageSender.USER)
                .content(content.trim())
                .sentAt(now)
                .build();
        m = messageRepository.save(m);
        session.getMessages().add(m);

        sseStreamService.pushToAdmin("new-message", toMessageDto(m));
        unlockChatAiAnalysisService.scheduleAnalysis(session.getId());
        return m;
    }

    @Transactional
    public UnlockChatMessage adminSendMessage(Long sessionId, String adminUsername, String content) {
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content required");
        }
        UnlockChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session introuvable"));
        if (!isSessionActiveForChat(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session fermée");
        }

        LocalDateTime now = LocalDateTime.now();
        UnlockChatMessage m = UnlockChatMessage.builder()
                .session(session)
                .sender(MessageSender.ADMIN)
                .content(content.trim())
                .sentAt(now)
                .build();
        m = messageRepository.save(m);
        session.getMessages().add(m);

        sseStreamService.pushToUser(session.getUserId(), "new-message", toMessageDto(m));
        // Re-run Groq with full transcript (user + admin); previously only userSendMessage triggered analysis.
        unlockChatAiAnalysisService.scheduleAnalysis(session.getId());
        return m;
    }

    @Transactional
    public void grantAccess(Long sessionId, String adminUsername) {
        UnlockChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session introuvable"));
        ChatSessionStatus st = session.getStatus();
        String admin = adminUsername != null ? adminUsername : "admin";

        // Déjà traité : évite 409 quand l’admin clique après un auto-déblocage IA (AI_RESOLVED) ou un double clic.
        if (st == ChatSessionStatus.GRANTED || st == ChatSessionStatus.AI_RESOLVED) {
            userBlockService.approveUnlock(session.getBlockId(), admin);
            sseStreamService.pushToUser(session.getUserId(), "unlock-decision", Map.of("decision", "granted"));
            return;
        }
        if (st == ChatSessionStatus.REJECTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Session déjà refusée — impossible d’accorder l’accès depuis cette conversation.");
        }
        if (!isSessionActiveForChat(st)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session fermée — statut: " + st);
        }

        session.setStatus(ChatSessionStatus.GRANTED);
        session.setClosedAt(LocalDateTime.now());
        session.setClosedBy(admin);
        sessionRepository.save(session);
        userBlockService.approveUnlock(session.getBlockId(), admin);
        sseStreamService.pushToUser(session.getUserId(), "unlock-decision", Map.of("decision", "granted"));
    }

    @Transactional
    public void rejectAccess(Long sessionId, String adminUsername, String reason) {
        UnlockChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session introuvable"));
        if (!isSessionActiveForChat(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session fermée");
        }
        String r = reason != null ? reason.strip() : "";
        String line = r.isEmpty() ? "Demande refusée." : r;

        LocalDateTime now = LocalDateTime.now();
        UnlockChatMessage note = UnlockChatMessage.builder()
                .session(session)
                .sender(MessageSender.ADMIN)
                .content(line)
                .sentAt(now)
                .build();
        session.getMessages().add(note);

        session.setStatus(ChatSessionStatus.REJECTED);
        session.setClosedAt(now);
        session.setClosedBy(adminUsername != null ? adminUsername : "admin");
        sessionRepository.save(session);

        userBlockService.rejectUnlock(session.getBlockId(), adminUsername, r);
        sseStreamService.pushToUser(session.getUserId(), "unlock-decision", Map.of("decision", "rejected"));
    }

    @Transactional(readOnly = true)
    public UnlockChatSessionDTO getSession(Long sessionId) {
        UnlockChatSession s = sessionRepository.findByIdWithMessages(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session introuvable"));
        return toSessionDto(s);
    }

    /**
     * Prefer an OPEN session; otherwise the latest GRANTED/REJECTED session so the client can resume UI after navigation.
     */
    @Transactional(readOnly = true)
    public Optional<UnlockChatSessionDTO> getResumeStateForUser(String userId) {
        userId = normalizeUserId(userId);
        if (userId.isEmpty()) {
            return Optional.empty();
        }
        Optional<UnlockChatSession> open = sessionRepository.findByUserIdAndStatus(userId, ChatSessionStatus.OPEN);
        if (open.isPresent()) {
            return sessionRepository.findByIdWithMessages(open.get().getId()).map(this::toSessionDto);
        }
        Optional<UnlockChatSession> inReview = sessionRepository.findByUserIdAndStatus(
                userId, ChatSessionStatus.NEEDS_HUMAN_REVIEW);
        if (inReview.isPresent()) {
            return sessionRepository.findByIdWithMessages(inReview.get().getId()).map(this::toSessionDto);
        }
        List<UnlockChatSession> history = sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (history.isEmpty()) {
            return Optional.empty();
        }
        UnlockChatSession latest = history.get(0);
        ChatSessionStatus st = latest.getStatus();
        if (st == ChatSessionStatus.GRANTED || st == ChatSessionStatus.REJECTED || st == ChatSessionStatus.AI_RESOLVED) {
            return sessionRepository.findByIdWithMessages(latest.getId()).map(this::toSessionDto);
        }
        return Optional.empty();
    }

    /** OPEN session only — used when recovering chat from the “blocked” step without resuming old GRANTED/REJECTED rows. */
    @Transactional(readOnly = true)
    public Optional<UnlockChatSessionDTO> getOpenSessionForUser(String userId) {
        userId = normalizeUserId(userId);
        if (userId.isEmpty()) {
            return Optional.empty();
        }
        Optional<UnlockChatSession> open = sessionRepository.findByUserIdAndStatus(userId, ChatSessionStatus.OPEN);
        if (open.isPresent()) {
            return sessionRepository.findByIdWithMessages(open.get().getId()).map(this::toSessionDto);
        }
        return sessionRepository
                .findByUserIdAndStatus(userId, ChatSessionStatus.NEEDS_HUMAN_REVIEW)
                .flatMap(s -> sessionRepository.findByIdWithMessages(s.getId()).map(this::toSessionDto));
    }

    @Transactional(readOnly = true)
    public List<UnlockChatSessionSummaryDTO> getOpenSessions() {
        return sessionRepository
                .findByStatusInOrderByCreatedAtDesc(EnumSet.of(ChatSessionStatus.OPEN, ChatSessionStatus.NEEDS_HUMAN_REVIEW))
                .stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    public UnlockChatMessageDTO toMessageDto(UnlockChatMessage m) {
        Long sid = null;
        if (m.getSession() != null) {
            sid = m.getSession().getId();
        }
        return UnlockChatMessageDTO.builder()
                .id(m.getId())
                .sessionId(sid)
                .sender(m.getSender())
                .content(m.getContent())
                .sentAt(m.getSentAt())
                .build();
    }

    private UnlockChatSessionDTO toSessionDto(UnlockChatSession s) {
        UserBlock block = userBlockService.findById(s.getBlockId()).orElse(null);
        List<UnlockChatMessage> ordered = messageRepository.findBySessionOrderBySentAtAsc(s);
        List<UnlockChatMessageDTO> msgs = ordered.stream().map(this::toMessageDto).collect(Collectors.toList());
        return UnlockChatSessionDTO.builder()
                .sessionId(s.getId())
                .userId(s.getUserId())
                .blockId(s.getBlockId())
                .status(s.getStatus())
                .blockReason(block != null ? block.getBlockReason() : null)
                .failureCount(block != null ? block.getFailureCount() : null)
                .blockedAt(block != null ? block.getBlockedAt() : null)
                .createdAt(s.getCreatedAt())
                .closedAt(s.getClosedAt())
                .closedBy(s.getClosedBy())
                .messages(msgs)
                .aiDecision(s.getAiDecision())
                .aiConfidence(s.getAiConfidence())
                .aiReasoning(s.getAiReasoning())
                .aiRecommendUnblock(s.getAiRecommendUnblock())
                .aiAnalyzedAt(s.getAiAnalyzedAt())
                .build();
    }

    private UnlockChatSessionSummaryDTO toSummary(UnlockChatSession s) {
        UserBlock block = userBlockService.findById(s.getBlockId()).orElse(null);
        long count = messageRepository.countBySession(s);
        Optional<UnlockChatMessage> last = messageRepository.findFirstBySessionOrderBySentAtDesc(s);
        long waiting = ChronoUnit.MINUTES.between(s.getCreatedAt(), LocalDateTime.now());
        return UnlockChatSessionSummaryDTO.builder()
                .sessionId(s.getId())
                .userId(s.getUserId())
                .userDisplayName(s.getUserId())
                .blockReason(block != null ? block.getBlockReason() : null)
                .messageCount(count)
                .lastMessagePreview(last.map(m -> preview(m.getContent(), 60)).orElse(""))
                .lastMessageAt(last.map(UnlockChatMessage::getSentAt).orElse(s.getCreatedAt()))
                .waitingMinutes(waiting)
                .build();
    }

    private static String preview(String text, int max) {
        String t = text != null ? text.trim() : "";
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static boolean isSessionActiveForChat(ChatSessionStatus status) {
        return status == ChatSessionStatus.OPEN || status == ChatSessionStatus.NEEDS_HUMAN_REVIEW;
    }
}
