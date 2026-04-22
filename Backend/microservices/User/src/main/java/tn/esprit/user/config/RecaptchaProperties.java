package tn.esprit.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * reCAPTCHA v2 côté serveur (siteverify).
 * Désactivez avec {@code skillio.recaptcha.enabled=false} si vous ne configurez pas de secret.
 */
@Data
@Component
@ConfigurationProperties(prefix = "skillio.recaptcha")
public class RecaptchaProperties {

    /** Si false, aucune vérification (inscription inchangée pour les environnements sans clé). */
    private boolean enabled = false;

    /** Secret serveur (reCAPTCHA admin). */
    private String secretKey = "";
}
