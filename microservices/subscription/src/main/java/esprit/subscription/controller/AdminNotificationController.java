package esprit.subscription.controller;

import esprit.subscription.DTO.admin.AdminNotificationResponse;
import esprit.subscription.config.LivenessAdminAuthHelper;
import esprit.subscription.service.AdminNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/admin/notifications", "/sub/admin/notifications"})
public class AdminNotificationController {

    private final AdminNotificationService adminNotificationService;
    private final LivenessAdminAuthHelper adminAuthHelper;

    public AdminNotificationController(
            AdminNotificationService adminNotificationService,
            LivenessAdminAuthHelper adminAuthHelper) {
        this.adminNotificationService = adminNotificationService;
        this.adminAuthHelper = adminAuthHelper;
    }

    @GetMapping
    public ResponseEntity<List<AdminNotificationResponse>> listUnread(HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        return ResponseEntity.ok(adminNotificationService.getUnreadNotifications());
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> countUnread(HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        return ResponseEntity.ok(Map.of("count", adminNotificationService.getUnreadCount()));
    }

    /** Résout le jeton (query {@code token}) — identifie l’alerte et l’utilisateur cible pour le chat admin. */
    @GetMapping("/resolve")
    public ResponseEntity<AdminNotificationResponse> resolveByToken(
            @RequestParam("token") String token, HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        return ResponseEntity.ok(adminNotificationService.findByActionToken(token));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id, HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        adminNotificationService.markAsRead(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead(HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        adminNotificationService.markAllAsRead();
        return ResponseEntity.noContent().build();
    }

    /**
     * Insère une notification de test (non bloquage) — vérifie secret + MySQL + liste cloche.
     * {@code POST /api/admin/notifications/debug/ui-test} avec header {@code X-Liveness-Admin-Secret}.
     */
    @PostMapping("/debug/ui-test")
    public ResponseEntity<AdminNotificationResponse> uiTest(HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        return ResponseEntity.ok(adminNotificationService.createUiTestNotification());
    }

    /** Regenerate {@code actionToken} for deep links (invalidates previous token). */
    @PostMapping("/{id}/refresh-token")
    public ResponseEntity<AdminNotificationResponse> refreshToken(
            @PathVariable Long id, HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        return ResponseEntity.ok(adminNotificationService.refreshActionToken(id));
    }
}
