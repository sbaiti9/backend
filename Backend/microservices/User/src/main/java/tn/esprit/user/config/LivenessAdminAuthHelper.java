package tn.esprit.user.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LivenessAdminAuthHelper {

    @Value("${liveness.admin.secret:}")
    private String adminSecret;

    public void requireAdminSecret(HttpServletRequest request) {
        if (adminSecret == null || adminSecret.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Configurer liveness.admin.secret pour les endpoints admin.");
        }
        String h = request.getHeader("X-Liveness-Admin-Secret");
        if (h == null || h.isBlank()) {
            h = request.getParameter("adminSecret");
        }
        if (h == null || !adminSecret.equals(h)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès administrateur refusé");
        }
    }
}
