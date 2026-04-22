package esprit.subscription.service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Component
public class LivenessPaymentGuard {

    private final LivenessService livenessService;
    private final UserBlockService userBlockService;

    public LivenessPaymentGuard(LivenessService livenessService, UserBlockService userBlockService) {
        this.livenessService = livenessService;
        this.userBlockService = userBlockService;
    }

    /**
     * @param token from JSON body {@code livenessToken} or {@code X-Liveness-Token} header
     * @return null if OK, or 403 with error body
     */
    public ResponseEntity<Map<String, String>> check(String token, long userId) {
        if (userBlockService.isPaymentBlocked(String.valueOf(userId))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "Account blocked",
                    "message", "Compte bloqué — contactez le support ou demandez un déblocage."));
        }
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "Liveness verification required",
                    "message", "Vérification d'identité requise"));
        }
        try {
            livenessService.assertAndConsumePaymentJwt(token, userId);
            return null;
        } catch (ResponseStatusException ex) {
            String msg = ex.getReason() != null ? ex.getReason() : "Liveness verification required";
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                    "error", msg,
                    "message", msg));
        }
    }
}
