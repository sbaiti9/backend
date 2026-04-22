package tn.esprit.training.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import tn.esprit.training.dto.PriceOptimizationItemDTO;
import tn.esprit.training.dto.TrainingResponseDTO;
import tn.esprit.training.repository.AvisRepository;
import tn.esprit.training.repository.TrainingRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AnthropicPriceOptimizationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TOKENS = 4096;

    private final AnthropicClientService anthropic;
    private final GroqClientService groq;
    private final TrainingRepository trainingRepository;
    private final AvisRepository avisRepository;

    public AnthropicPriceOptimizationService(
            AnthropicClientService anthropic,
            GroqClientService groq,
            TrainingRepository trainingRepository,
            AvisRepository avisRepository
    ) {
        this.anthropic = anthropic;
        this.groq = groq;
        this.trainingRepository = trainingRepository;
        this.avisRepository = avisRepository;
    }

    public List<PriceOptimizationItemDTO> optimizePrices() {
        var trainings = trainingRepository.findAll();
        if (trainings.isEmpty()) {
            return List.of();
        }
        List<TrainingResponseDTO> dtos = trainings.stream().map(t -> {
            TrainingResponseDTO d = new TrainingResponseDTO();
            d.setId(t.getId());
            d.setTitle(t.getTitle());
            d.setCategory(t.getCategory());
            d.setLevel(t.getLevel());
            d.setPrice(t.getPrice());
            Double avg = avisRepository.averageRatingByTrainingId(t.getId());
            d.setAverageRating(avg != null ? Math.round(avg * 10.0) / 10.0 : null);
            long rc = avisRepository.countByTraining_Id(t.getId());
            d.setReviewsCount((int) rc);
            return d;
        }).collect(Collectors.toList());

        try {
            String catalog = MAPPER.writeValueAsString(dtos);
            String prompt = "You are a pricing analyst for online courses. Given this JSON catalog of courses (id, title, category, level, price, averageRating, reviewsCount), "
                    + "suggest an optimal price for each course. Respond with ONLY a JSON array (no markdown), each item: "
                    + "{\"courseId\":number,\"currentPrice\":number,\"suggestedPrice\":number,\"reason\":\"short text\",\"status\":\"overpriced|underpriced|optimal\"}. "
                    + "Catalog:\n" + catalog;
            String raw;
            try {
                raw = groq.chat(prompt);
            } catch (Exception groqFailed) {
                raw = anthropic.callClaude(prompt, TOKENS);
            }
            return parseItems(raw, dtos);
        } catch (Exception e) {
            return heuristicFallback(dtos);
        }
    }

    private List<PriceOptimizationItemDTO> parseItems(String raw, List<TrainingResponseDTO> dtos) {
        List<PriceOptimizationItemDTO> out = new ArrayList<>();
        try {
            String json = extractJsonArray(raw.trim());
            JsonNode arr = MAPPER.readTree(json);
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    long courseId = n.path("courseId").asLong();
                    double current = n.path("currentPrice").asDouble();
                    double suggested = n.path("suggestedPrice").asDouble();
                    String reason = n.path("reason").asText("");
                    String status = n.path("status").asText("optimal");
                    out.add(new PriceOptimizationItemDTO(courseId, current, suggested, reason, status));
                }
            }
        } catch (Exception ignored) {
        }
        if (out.isEmpty()) {
            return heuristicFallback(dtos);
        }
        return out;
    }

    private List<PriceOptimizationItemDTO> heuristicFallback(List<TrainingResponseDTO> dtos) {
        List<PriceOptimizationItemDTO> out = new ArrayList<>();
        for (TrainingResponseDTO t : dtos) {
            double price = t.getPrice() != null ? t.getPrice().doubleValue() : 0;
            double rating = t.getAverageRating() != null ? t.getAverageRating() : 0;
            double suggested = price;
            String status = "optimal";
            String reason = "Heuristic: maintain price.";
            if (rating >= 4.5 && t.getReviewsCount() != null && t.getReviewsCount() >= 5) {
                suggested = Math.round(price * 1.1 * 100.0) / 100.0;
                status = "underpriced";
                reason = "Strong ratings and reviews — slight increase suggested.";
            } else if (rating > 0 && rating < 3.0) {
                suggested = Math.round(price * 0.9 * 100.0) / 100.0;
                status = "overpriced";
                reason = "Low ratings — consider a discount.";
            }
            out.add(new PriceOptimizationItemDTO(t.getId() != null ? t.getId() : 0, price, suggested, reason, status));
        }
        return out;
    }

    private static String extractJsonArray(String s) {
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }
}

