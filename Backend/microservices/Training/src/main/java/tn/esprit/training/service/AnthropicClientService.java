package tn.esprit.training.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Single shared Anthropic Messages API client used by all AI features in this service.
 */
@Service
public class AnthropicClientService {

    @Value("${anthropic.api.key}")
    private String anthropicApiKey;

    private final RestTemplate restTemplate;

    public AnthropicClientService() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000);
        f.setReadTimeout(30_000);
        this.restTemplate = new RestTemplate(f);
    }

    public String callClaude(String prompt) {
        return callClaude(prompt, 1500);
    }

    public String callClaude(String prompt, int maxTokens) {
        if (anthropicApiKey == null || anthropicApiKey.isBlank()) {
            throw new IllegalStateException("Anthropic API key is not configured (set ANTHROPIC_API_KEY)");
        }
        String url = "https://api.anthropic.com/v1/messages";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", anthropicApiKey);
        headers.set("anthropic-version", "2023-06-01");
        Map<String, Object> body = Map.of(
                "model", "claude-haiku-4-5-20251001",
                "max_tokens", maxTokens,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        final Map<String, Object> response;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> tmp = restTemplate.postForObject(url, entity, Map.class);
            response = tmp;
        } catch (RestClientResponseException ex) {
            String details = ex.getResponseBodyAsString();
            String msg = "Anthropic API request failed (HTTP " + ex.getStatusCode().value() + ")";
            if (details != null && !details.isBlank()) {
                msg += ": " + details;
            }
            throw new IllegalStateException(msg);
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "Anthropic API request failed";
            throw new IllegalStateException(msg);
        }
        if (response == null) {
            throw new IllegalStateException("Empty response from Anthropic API");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty()) {
            throw new IllegalStateException("Anthropic response missing content");
        }
        Map<String, Object> block = content.get(0);
        Object text = block.get("text");
        return text != null ? text.toString() : "";
    }
}

