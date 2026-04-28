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

@Service
public class GroqClientService {

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    @Value("${groq.vision.model:meta-llama/llama-4-scout-17b-16e-instruct}")
    private String groqVisionModel;

    private final RestTemplate restTemplate;

    public GroqClientService() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000);
        f.setReadTimeout(30_000);
        this.restTemplate = new RestTemplate(f);
    }

    public String chat(String prompt) {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("Groq API key is not configured (set GROQ_API_KEY)");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        Map<String, Object> body = Map.of(
                "model", groqModel,
                "temperature", 0.4,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        final Map<String, Object> response;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> tmp = restTemplate.postForObject(groqApiUrl, entity, Map.class);
            response = tmp;
        } catch (RestClientResponseException ex) {
            String details = ex.getResponseBodyAsString();
            String msg = "Groq API request failed (HTTP " + ex.getStatusCode().value() + ")";
            if (details != null && !details.isBlank()) {
                msg += ": " + details;
            }
            throw new IllegalStateException(msg);
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "Groq API request failed";
            throw new IllegalStateException(msg);
        }

        if (response == null) {
            throw new IllegalStateException("Empty response from Groq API");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("Groq response missing choices");
        }
        Map<String, Object> first = choices.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) first.get("message");
        if (message == null) {
            throw new IllegalStateException("Groq response missing message");
        }
        Object content = message.get("content");
        return content != null ? content.toString() : "";
    }

    /** One text prompt + one JPEG frame (base64 without data URL prefix). */
    public String chatWithImage(String prompt, String base64JpegWithoutPrefix) {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("Groq API key is not configured (set GROQ_API_KEY)");
        }
        if (base64JpegWithoutPrefix == null || base64JpegWithoutPrefix.isBlank()) {
            throw new IllegalArgumentException("base64 image is required");
        }

        String dataUrl = "data:image/jpeg;base64," + base64JpegWithoutPrefix.trim();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        Map<String, Object> textPart = Map.of("type", "text", "text", prompt);
        Map<String, Object> imagePart = Map.of("type", "image_url", "image_url", Map.of("url", dataUrl));
        Map<String, Object> userMessage = Map.of(
                "role", "user",
                "content", List.of(textPart, imagePart)
        );

        String model = groqVisionModel != null && !groqVisionModel.isBlank()
                ? groqVisionModel
                : "meta-llama/llama-4-scout-17b-16e-instruct";

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.15,
                "messages", List.of(userMessage)
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        final Map<String, Object> response;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> tmp = restTemplate.postForObject(groqApiUrl, entity, Map.class);
            response = tmp;
        } catch (RestClientResponseException ex) {
            String details = ex.getResponseBodyAsString();
            String msg = "Groq API request failed (HTTP " + ex.getStatusCode().value() + ")";
            if (details != null && !details.isBlank()) {
                msg += ": " + details;
            }
            throw new IllegalStateException(msg);
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "Groq API request failed";
            throw new IllegalStateException(msg);
        }

        if (response == null) {
            throw new IllegalStateException("Empty response from Groq API");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("Groq response missing choices");
        }
        Map<String, Object> first = choices.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) first.get("message");
        if (message == null) {
            throw new IllegalStateException("Groq response missing message");
        }
        Object content = message.get("content");
        return content != null ? content.toString() : "";
    }
}

