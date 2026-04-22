package esprit.subscription.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import esprit.subscription.config.GroqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Groq OpenAI-compatible chat completions (JSON mode).
 */
@Component
public class GroqChatClient {

    private static final Logger log = LoggerFactory.getLogger(GroqChatClient.class);

    private final RestTemplate restTemplate;
    private final GroqProperties props;
    private final ObjectMapper objectMapper;

    public GroqChatClient(RestTemplate restTemplate, GroqProperties props, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * @return assistant message content (JSON string), or empty if disabled / error
     */
    public Optional<String> completeJson(String systemPrompt, String userContent) {
        return completeJson(systemPrompt, userContent, 0.22);
    }

    /**
     * @param temperature higher values (e.g. 0.55–0.7) reduce identical scores on repeated admin re-analysis
     */
    public Optional<String> completeJson(String systemPrompt, String userContent, double temperature) {
        if (!props.isEnabled() || props.getKey() == null || props.getKey().isBlank()) {
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(props.getKey().trim());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", props.getModel());
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userContent)
            ));
            body.put("temperature", Math.max(0.0, Math.min(1.5, temperature)));
            body.put("response_format", Map.of("type", "json_object"));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String raw = restTemplate.postForObject(props.getUrl(), entity, String.class);
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                log.warn("Groq response missing choices[0].message.content");
                return Optional.empty();
            }
            return Optional.of(content.asText());
        } catch (Exception e) {
            log.warn("Groq API call failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
