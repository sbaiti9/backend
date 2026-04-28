package tn.esprit.training.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import tn.esprit.training.dto.SentimentResponseDTO;
import tn.esprit.training.entity.Avis;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AnthropicSentimentService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_OBJ = Pattern.compile("\\{[^{}]*\"mood\"[^{}]*}");

    private final AnthropicClientService anthropic;

    public AnthropicSentimentService(AnthropicClientService anthropic) {
        this.anthropic = anthropic;
    }

    public SentimentResponseDTO analyzeReviewsWithFallback(List<Avis> reviews) {
        SentimentResponseDTO fallback = ratingFallback(reviews);
        try {
            StringBuilder sb = new StringBuilder();
            for (Avis a : reviews) {
                int r = a.getRating() != null ? a.getRating() : 0;
                String c = a.getComment() != null ? a.getComment() : "";
                sb.append("rating=").append(r).append(" comment=").append(c).append("\n");
            }
            String prompt = "You analyze learner reviews for an online course. Given the lines below, respond with ONLY valid JSON (no markdown) "
                    + "with keys: mood (one of: positive, neutral, negative), score (integer 0-100), summary (one short paragraph). Reviews:\n"
                    + sb;
            String raw = anthropic.callClaude(prompt);
            SentimentResponseDTO parsed = parseJsonSentiment(raw);
            if (parsed != null) {
                parsed.setSource("ai");
                return parsed;
            }
        } catch (Exception ignored) {
        }
        fallback.setSource("rating");
        return fallback;
    }

    private static SentimentResponseDTO ratingFallback(List<Avis> reviews) {
        double avg = reviews.stream()
                .mapToInt(a -> a.getRating() != null ? a.getRating() : 0)
                .average()
                .orElse(0);
        int score = (int) Math.round((avg / 5.0) * 100);
        String mood;
        if (avg >= 4.0) mood = "positive";
        else if (avg >= 3.0) mood = "neutral";
        else mood = "negative";
        String summary = "Estimated from average star rating (" + String.format("%.1f", avg) + " / 5).";
        return new SentimentResponseDTO(mood, score, summary);
    }

    private static SentimentResponseDTO parseJsonSentiment(String raw) {
        try {
            String s = raw.trim();
            JsonNode root = MAPPER.readTree(extractJsonObject(s));
            String mood = root.path("mood").asText("neutral");
            int score = root.path("score").asInt(50);
            String summary = root.path("summary").asText("");
            return new SentimentResponseDTO(mood, Math.max(0, Math.min(100, score)), summary);
        } catch (Exception e) {
            Matcher m = JSON_OBJ.matcher(raw);
            if (m.find()) {
                try {
                    JsonNode root = MAPPER.readTree(m.group());
                    String mood = root.path("mood").asText("neutral");
                    int score = root.path("score").asInt(50);
                    String summary = root.path("summary").asText("");
                    return new SentimentResponseDTO(mood, Math.max(0, Math.min(100, score)), summary);
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static String extractJsonObject(String s) {
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }
}

