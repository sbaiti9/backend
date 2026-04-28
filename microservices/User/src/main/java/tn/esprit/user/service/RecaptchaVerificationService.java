package tn.esprit.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import tn.esprit.user.config.RecaptchaProperties;

/**
 * Vérifie le jeton reCAPTCHA v2 auprès de Google {@code siteverify}.
 */
@Service
@RequiredArgsConstructor
public class RecaptchaVerificationService {

    private static final Logger log = LoggerFactory.getLogger(RecaptchaVerificationService.class);
    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    private final RecaptchaProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * @return true si la vérification est OK ou si reCAPTCHA est désactivé côté config.
     */
    public boolean verifyOrDisabled(String token) {
        if (!props.isEnabled()) {
            return true;
        }
        String secret = props.getSecretKey() == null ? "" : props.getSecretKey().trim();
        if (secret.isEmpty()) {
            log.warn("skillio.recaptcha.enabled=true but secret key is empty — skipping verification");
            return true;
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("secret", secret);
            form.add("response", token.trim());
            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(VERIFY_URL, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return false;
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
            boolean ok = root.path("success").asBoolean(false);
            if (!ok) {
                log.warn("reCAPTCHA siteverify success=false, error-codes: {}", root.path("error-codes"));
            }
            return ok;
        } catch (Exception e) {
            log.warn("reCAPTCHA siteverify failed: {}", e.getMessage());
            return false;
        }
    }
}
