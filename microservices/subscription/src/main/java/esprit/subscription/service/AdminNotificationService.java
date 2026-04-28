package esprit.subscription.service;

import esprit.subscription.DTO.admin.AdminNotificationResponse;
import esprit.subscription.entity.AdminNotification;
import esprit.subscription.repository.AdminNotificationRepository;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AdminNotificationService.class);

    private final AdminNotificationRepository repository;
    private final SseStreamService sseStreamService;

    public AdminNotificationService(AdminNotificationRepository repository, SseStreamService sseStreamService) {
        this.repository = repository;
        this.sseStreamService = sseStreamService;
    }

    /** Pour vérifier la cloche sans passer par un blocage liveness (secret admin requis). */
    @Transactional
    public AdminNotificationResponse createUiTestNotification() {
        AdminNotification n = createNotification(
                "ADMIN_UI_TEST",
                null,
                "0",
                "Test cloche : API + base OK. Marquez comme lu après vérification.",
                null,
                null);
        return toDto(n);
    }

    @Transactional
    public AdminNotification createNotification(
            String type,
            Long sessionId,
            String userId,
            String message,
            String aiDecision,
            Integer aiConfidence) {
        AdminNotification n = AdminNotification.builder()
                .type(type)
                .sessionId(sessionId)
                .userId(userId)
                .message(message)
                .aiDecision(aiDecision)
                .aiConfidence(aiConfidence)
                .refUserBlockId(null)
                .actionToken(UUID.randomUUID().toString())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
        return repository.save(n);
    }

    /**
     * First-time block row inserted in {@code user_blocks} (e.g. liveness failures). Not called on updates.
     * Uses a new transaction so a failure here never rolls back {@code user_blocks} persistence.
     * Does not throw — callers rely on block row being committed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AdminNotification notifyUserBlocked(String userId, String reason, Long userBlockId) {
        try {
            AdminNotification n = AdminNotification.builder()
                    .type("USER_BLOCKED")
                    .sessionId(null)
                    .userId(userId)
                    .message(reason != null ? reason : "Utilisateur bloqué")
                    .aiDecision(null)
                    .aiConfidence(null)
                    .refUserBlockId(userBlockId)
                    .actionToken(UUID.randomUUID().toString())
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            n = repository.save(n);
            log.info(
                    "Notification USER_BLOCKED créée id={} userId={} blockId={} (cloche + SSE)",
                    n.getId(),
                    userId,
                    userBlockId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "USER_BLOCKED");
            payload.put("notificationId", n.getId());
            payload.put("userId", userId);
            payload.put("userBlockId", userBlockId);
            payload.put("actionToken", n.getActionToken());
            payload.put("message", n.getMessage());
            sseStreamService.sendToAdmins("USER_BLOCKED", payload);
            sseStreamService.pushToAdmin("USER_BLOCKED", payload);
            return n;
        } catch (Exception e) {
            log.warn(
                    "notifyUserBlocked failed (user_block still saved elsewhere): {}",
                    e.getMessage(),
                    e);
            return null;
        }
    }

    /** Regenerate {@link AdminNotification#getActionToken()} (e.g. after sharing an old link). */
    @Transactional
    public AdminNotificationResponse refreshActionToken(Long notificationId) {
        AdminNotification n = repository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification introuvable"));
        n.setActionToken(UUID.randomUUID().toString());
        repository.save(n);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationId", n.getId());
        payload.put("userId", n.getUserId());
        payload.put("actionToken", n.getActionToken());
        sseStreamService.sendToAdmins("NOTIFICATION_TOKEN_REFRESHED", payload);
        sseStreamService.pushToAdmin("NOTIFICATION_TOKEN_REFRESHED", payload);
        return toDto(n);
    }

    @Transactional(readOnly = true)
    public List<AdminNotificationResponse> getUnreadNotifications() {
        return repository.findByIsReadFalseOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Résout le jeton unique ({@link AdminNotification#getActionToken()}) pour ouvrir le bon utilisateur / session
     * depuis la cloche ou un lien partagé — une ligne de notification par événement (ex. blocage).
     */
    @Transactional(readOnly = true)
    public AdminNotificationResponse findByActionToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "token required");
        }
        return repository
                .findByActionToken(token.trim())
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jeton inconnu ou expiré"));
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        return repository.countByIsReadFalse();
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        repository.markReadById(notificationId);
    }

    @Transactional
    public void markAllAsRead() {
        repository.markAllRead();
    }

    private AdminNotificationResponse toDto(AdminNotification n) {
        return AdminNotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .sessionId(n.getSessionId())
                .userId(n.getUserId())
                .refUserBlockId(n.getRefUserBlockId())
                .actionToken(n.getActionToken())
                .message(n.getMessage())
                .aiDecision(n.getAiDecision())
                .aiConfidence(n.getAiConfidence())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
