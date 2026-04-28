package tn.esprit.user.controller;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.user.config.LivenessAdminAuthHelper;
import tn.esprit.user.dto.GeoAuthEventResponse;
import tn.esprit.user.entity.Users;
import tn.esprit.user.service.GeoSecurityService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user/admin/geo-security")
@RequiredArgsConstructor
public class GeoSecurityAdminController {

    private final GeoSecurityService geoSecurityService;
    private final LivenessAdminAuthHelper livenessAdminAuthHelper;

    @GetMapping("/events")
    public List<GeoAuthEventResponse> events(
            @RequestParam(defaultValue = "72") int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(Math.max(1, Math.min(hours, 720)));
        return geoSecurityService.recentEvents(since).stream()
                .map(GeoAuthEventResponse::from)
                .toList();
    }

    @GetMapping("/anomalies")
    public List<GeoAuthEventResponse> anomalies(
            @RequestParam(defaultValue = "168") int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(Math.max(1, Math.min(hours, 720)));
        return geoSecurityService.recentAnomalies(since).stream()
                .map(GeoAuthEventResponse::from)
                .toList();
    }

    /**
     * Historique des événements géo pour un utilisateur (admin).
     * GET /user/admin/geo-security/users/{userId}/recent-events?limit=40
     */
    @GetMapping("/users/{userId}/recent-events")
    public ResponseEntity<List<GeoAuthEventResponse>> userRecentEvents(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "40") int limit) {
        try {
            List<GeoAuthEventResponse> rows = geoSecurityService.recentEventsForUser(userId, limit).stream()
                    .map(GeoAuthEventResponse::from)
                    .toList();
            return ResponseEntity.ok(rows);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Démo : insère deux LOGIN (Tunis → Paris) pour déclencher une anomalie sans VPN.
     */
    @PostMapping("/simulate-impossible-travel")
    public ResponseEntity<?> simulateImpossibleTravel(@RequestParam Long userId) {
        try {
            geoSecurityService.simulateImpossibleTravelDemo(userId);
            return ResponseEntity.ok(Map.of("ok", true, "userId", userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/users/{userId}/freeze-and-invalidate")
    public ResponseEntity<?> freezeAndInvalidate(@PathVariable Long userId) {
        try {
            Users u = geoSecurityService.freezeAndInvalidateTokens(userId);
            return ResponseEntity.ok(Map.of(
                    "userId", u.getId(),
                    "status", u.getStatus().name(),
                    "tokenVersion", u.getTokenVersion()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Hard-freeze endpoint independent from JWT authentication.
     * Protection is done via X-Liveness-Admin-Secret header.
     */
    @PostMapping("/users/{userId}/hard-freeze")
    public ResponseEntity<?> hardFreezeNoJwt(@PathVariable Long userId, HttpServletRequest request) {
        try {
            livenessAdminAuthHelper.requireAdminSecret(request);
            Users u = geoSecurityService.freezeAndInvalidateTokens(userId);
            return ResponseEntity.ok(Map.of(
                    "userId", u.getId(),
                    "status", u.getStatus().name(),
                    "tokenVersion", u.getTokenVersion(),
                    "mode", "hard-freeze-no-jwt"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/users/{userId}/warning-email-draft")
    public ResponseEntity<?> warningEmailDraft(@PathVariable Long userId) {
        try {
            GeoSecurityService.AdminEmailDraft draft = geoSecurityService.buildFreezeWarningEmailDraft(userId);
            return ResponseEntity.ok(draft);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Unable to build warning email draft",
                    "detail", e.getMessage() != null ? e.getMessage() : "unknown"));
        }
    }

    @PostMapping("/users/{userId}/warning-email-send")
    public ResponseEntity<?> sendWarningEmail(@PathVariable Long userId, @RequestBody Map<String, String> payload) {
        try {
            String to = payload.getOrDefault("to", "");
            String subject = payload.getOrDefault("subject", "");
            String body = payload.getOrDefault("body", "");
            geoSecurityService.sendWarningEmail(userId, to, subject, body);
            return ResponseEntity.ok(Map.of("sent", true, "userId", userId, "to", to));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Unable to send email",
                    "detail", e.getMessage() != null ? e.getMessage() : "unknown"));
        }
    }
}
