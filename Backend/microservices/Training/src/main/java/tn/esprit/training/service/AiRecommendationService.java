package tn.esprit.training.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import tn.esprit.training.entity.Training;
import tn.esprit.training.repository.AvisRepository;
import tn.esprit.training.repository.TrainingRepository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
public class AiRecommendationService {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TrainingRepository trainingRepository;
    private final AvisRepository avisRepository;
    private final AnthropicClientService anthropicClientService;
    private final GroqClientService groq;

    public AiRecommendationService(
            TrainingRepository trainingRepository,
            AvisRepository avisRepository,
            AnthropicClientService anthropicClientService,
            GroqClientService groq
    ) {
        this.trainingRepository = trainingRepository;
        this.avisRepository = avisRepository;
        this.anthropicClientService = anthropicClientService;
        this.groq = groq;
    }

    public String recommend() throws Exception {
        List<Training> trainings = trainingRepository.findAll();
        if (trainings.isEmpty()) {
            return "{\"choices\":[{\"message\":{\"content\":\"[]\"}}]}";
        }
        String dataset = trainings.stream().map(t -> {
            Double avg = avisRepository.averageRatingByTrainingId(t.getId());
            double rating = avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0;
            return String.format("{\"title\":\"%s\",\"price\":%s,\"level\":\"%s\",\"averageRating\":%s}",
                    escape(t.getTitle()), t.getPrice().toPlainString(), escape(t.getLevel()), rating);
        }).collect(Collectors.joining(",", "[", "]"));

        String prompt = "Recommend the top 3 trainings based on price, level, and rating. "
                + "Provide JSON array of 3 items with fields: title, reason. "
                + "Use this dataset: " + dataset;

        try {
            String content;
            try {
                content = groq.chat(prompt);
            } catch (Exception groqFailed) {
                content = anthropicClientService.callClaude(prompt);
            }
            return MAPPER.writeValueAsString(Map.of(
                    "choices", List.of(Map.of("message", Map.of("content", content)))
            ));
        } catch (Exception e) {
            List<Training> sorted = trainings.stream()
                    .sorted((a, b) -> {
                        Double ar = avisRepository.averageRatingByTrainingId(a.getId());
                        Double br = avisRepository.averageRatingByTrainingId(b.getId());
                        double aa = ar != null ? ar : 0.0;
                        double bb = br != null ? br : 0.0;
                        return Double.compare(bb, aa);
                    })
                    .limit(3)
                    .toList();
            String arr = sorted.stream().map(t -> {
                Double avg = avisRepository.averageRatingByTrainingId(t.getId());
                double rating = avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0;
                String reason = "High rating (" + rating + "★) and good value.";
                return String.format("{\"title\":\"%s\",\"reason\":\"%s\"}", escape(t.getTitle()), escape(reason));
            }).collect(Collectors.joining(",", "[", "]"));
            return MAPPER.writeValueAsString(Map.of(
                    "choices", List.of(Map.of("message", Map.of("content", arr)))
            ));
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public List<Map<String, Object>> recommendItems() throws Exception {
        List<Training> trainings = trainingRepository.findAll();
        if (trainings.isEmpty()) {
            return Collections.emptyList();
        }
        String dataset = trainings.stream().map(t -> {
            Double avg = avisRepository.averageRatingByTrainingId(t.getId());
            double rating = avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0;
            return String.format("{\"id\":%s,\"title\":\"%s\",\"price\":%s,\"level\":\"%s\",\"averageRating\":%s}",
                    t.getId(), escape(t.getTitle()), t.getPrice().toPlainString(), escape(t.getLevel()), rating);
        }).collect(Collectors.joining(",", "[", "]"));

        String prompt = "Recommend the top 3 trainings based on price, level, and rating. "
                + "Respond with ONLY a JSON array of 3 objects with fields courseId and reason (courseId must be one of the dataset ids). Dataset: " + dataset;

        try {
            String raw;
            try {
                raw = groq.chat(prompt);
            } catch (Exception groqFailed) {
                raw = anthropicClientService.callClaude(prompt);
            }
            List<Map<String, String>> parsed = parseItemsArray(raw);
            return enrichWithIds(parsed, trainings);
        } catch (Exception e) {
            return enrichWithIds(recommendItemsFallback(trainings), trainings);
        }
    }

    private List<Map<String, String>> recommendItemsFallback(List<Training> trainings) {
        List<Training> sorted = trainings.stream()
                .sorted((a, b) -> {
                    Double ar = avisRepository.averageRatingByTrainingId(a.getId());
                    Double br = avisRepository.averageRatingByTrainingId(b.getId());
                    return Double.compare(br != null ? br : 0.0, ar != null ? ar : 0.0);
                })
                .limit(3)
                .toList();
        return sorted.stream().map(t -> {
            Double avg = avisRepository.averageRatingByTrainingId(t.getId());
            double rating = avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0;
            return Map.of(
                    "courseId", String.valueOf(t.getId() != null ? t.getId() : 0),
                    "title", t.getTitle() != null ? t.getTitle() : "",
                    "reason", "High rating (" + rating + "★) and good value."
            );
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> enrichWithIds(List<Map<String, String>> items, List<Training> trainings) {
        Map<String, Long> idByTitle = new HashMap<>();
        for (Training t : trainings) {
            if (t.getId() != null && t.getTitle() != null) {
                idByTitle.put(t.getTitle().trim().toLowerCase(), t.getId());
            }
        }

        return (items == null ? List.<Map<String, String>>of() : items).stream().map(m -> {
            String title = m.getOrDefault("title", "");
            String reason = m.getOrDefault("reason", "");
            String cidRaw = m.getOrDefault("courseId", m.getOrDefault("id", ""));
            long cid = 0L;
            try {
                if (cidRaw != null && !cidRaw.isBlank()) {
                    cid = Long.parseLong(cidRaw.replaceAll("[^0-9]", ""));
                }
            } catch (Exception ignored) {
            }
            if (cid <= 0 && title != null && !title.isBlank()) {
                Long hit = idByTitle.get(title.trim().toLowerCase());
                cid = hit != null ? hit : 0L;
            }
            return Map.<String, Object>of(
                    "courseId", cid,
                    "title", title != null ? title : "",
                    "reason", reason != null ? reason : ""
            );
        }).toList();
    }

    private List<Map<String, String>> parseItemsArray(String raw) {
        try {
            int start = raw.indexOf('[');
            int end = raw.lastIndexOf(']');
            if (start >= 0 && end > start) {
                String json = raw.substring(start, end + 1);
                return MAPPER.readValue(json, new TypeReference<List<Map<String, String>>>() {});
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }
}

