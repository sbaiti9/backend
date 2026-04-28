package esprit.subscription.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Component
public class LivenessAdminAuthHelper {

    @Value("${liveness.admin.secret:}")
    private String adminSecret;

    /**
     * Hourly rotating code derived from {@code liveness.admin.secret} (HMAC-SHA256).
     * Shown in human-review emails so admins can use the unlock-chat admin API.
     */
    public String getCurrentCode() {
        if (adminSecret == null || adminSecret.isBlank()) {
            return "(non configuré)";
        }
        long hourEpoch = Instant.now().truncatedTo(ChronoUnit.HOURS).getEpochSecond();
        return hmacCodeForHour(hourEpoch);
    }

    private String hmacCodeForHour(long hourEpochSeconds) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(adminSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(String.valueOf(hourEpochSeconds).getBytes(StandardCharsets.UTF_8));
            String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            String alnum = b64.replaceAll("[^A-Za-z0-9]", "");
            if (alnum.length() >= 6) {
                return alnum.substring(0, 6).toUpperCase();
            }
            String hex = bytesToHex(raw);
            return hex.substring(0, Math.min(6, hex.length())).toUpperCase();
        } catch (Exception e) {
            return "??????";
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

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
