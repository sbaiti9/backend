package tn.esprit.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.esprit.user.dto.LoginGeoSnapshot;
import tn.esprit.user.entity.GeoAuthEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Construit le résumé de localisation au login (ville/pays issus de la géo-IP) et une courte phrase
 * d’accueil en français via Groq si configuré.
 */
@Service
@RequiredArgsConstructor
public class LoginLocationAiService {

    private static final Logger log = LoggerFactory.getLogger(LoginLocationAiService.class);

    private final GroqChatClient groqChatClient;
    private final ObjectMapper objectMapper;

    public LoginGeoSnapshot describe(GeoAuthEvent ev) {
        if (ev == null) {
            return LoginGeoSnapshot.empty();
        }
        String city = blankToNull(ev.getCity());
        String country = blankToNull(ev.getCountry());
        Double lat = ev.getLatitude();
        Double lon = ev.getLongitude();
        String ai = buildWelcomeMessageFr(city, country);
        return new LoginGeoSnapshot(city, country, lat, lon, ai == null ? "" : ai);
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String buildWelcomeMessageFr(String city, String country) {
        if (city == null && country == null) {
            return "";
        }
        Map<String, String> ctx = new LinkedHashMap<>();
        if (city != null) {
            ctx.put("approxCity", city);
        }
        if (country != null) {
            ctx.put("approxCountry", country);
        }
        try {
            String userJson = objectMapper.writeValueAsString(ctx);
            String system = """
                    Tu es l’assistant Skillio. Réponds uniquement avec un objet JSON valide, une seule clé :
                    {"messageFr":"..."}
                    Règles pour messageFr :
                    - une ou deux phrases en français, ton professionnel et chaleureux ;
                    - accueille l’utilisateur et évoque sa zone de connexion approximative (ville et/ou pays fournis) ;
                    - ne mentionne jamais d’adresse IP ni de coordonnées GPS précises ;
                    - maximum 220 caractères.
                    """;
            return groqChatClient.completeJson(system, userJson, 0.35)
                    .map(this::parseMessageFr)
                    .filter(s -> !s.isBlank())
                    .orElse("");
        } catch (Exception e) {
            log.debug("Login location AI skipped: {}", e.getMessage());
            return "";
        }
    }

    private String parseMessageFr(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode m = root.path("messageFr");
            if (m.isTextual()) {
                return m.asText("").trim();
            }
        } catch (Exception e) {
            log.debug("Login location AI JSON parse failed: {}", e.getMessage());
        }
        return "";
    }
}
