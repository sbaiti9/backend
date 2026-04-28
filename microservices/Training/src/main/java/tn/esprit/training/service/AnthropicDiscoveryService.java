package tn.esprit.training.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import tn.esprit.training.dto.LearningPathStepDTO;
import tn.esprit.training.dto.TrainingResponseDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AnthropicDiscoveryService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TOKENS = 4096;

    private final AnthropicClientService anthropic;
    private final GroqClientService groq;

    public AnthropicDiscoveryService(AnthropicClientService anthropic, GroqClientService groq) {
        this.anthropic = anthropic;
        this.groq = groq;
    }

    public List<LearningPathStepDTO> generateLearningPath(String goal, List<TrainingResponseDTO> courses) {
        if (courses.isEmpty()) {
            return List.of();
        }
        try {
            String catalogJson = buildCatalogJsonForLlm(courses);
            String validIds = buildValidIdCsv(courses);
            String prompt = "You are building a sequential learning path for an online course platform.\n"
                    + "Learner goal: " + goal + "\n\n"
                    + "Course catalog (JSON array, one object per real course — use ONLY these course ids): " + catalogJson + "\n\n"
                    + "The ONLY valid numeric courseId values are exactly: " + validIds + "\n"
                    + "Respond with ONLY a JSON array (no markdown fences, no commentary). Each element must be an object with exactly:\n"
                    + "{\"step\":<1-based order>,\"courseId\":<must exist in catalog>,\"title\":string,\"reason\":string,"
                    + "\"estimatedWeeks\":number,\"category\":string,\"level\":string,\"price\":number,\"thumbnailUrl\":string}.\n"
                    + "Pick 3–6 courses that best achieve the goal, in a sensible order. "
                    + "Do not invent courseIds; copying title/category/level/price/thumbnailUrl from the matching catalog row is fine.";

            String raw;
            try {
                raw = groq.chat(prompt);
            } catch (Exception groqFailed) {
                raw = anthropic.callClaude(prompt, TOKENS);
            }
            return parseLearningPath(raw, courses, goal);
        } catch (Exception e) {
            return fallbackPath(goal, courses);
        }
    }

    public List<TrainingResponseDTO> smartSearch(String query, List<TrainingResponseDTO> courses) {
        if (courses.isEmpty()) {
            return List.of();
        }
        String q = query.toLowerCase(Locale.ROOT).trim();
        try {
            String catalogJson = buildCatalogJsonForLlm(courses);
            String prompt = "You rank online courses for a search box.\n"
                    + "User query: \"" + query + "\"\n\n"
                    + "All courses in the database (JSON array — you must only reference ids from this list): " + catalogJson + "\n\n"
                    + "Return ONLY a JSON array of integers: course ids ordered from most relevant to least relevant. "
                    + "Include every course id that is a reasonable match; omit only clear non-matches. "
                    + "Consider level (e.g. beginner), language (e.g. English), category, and description text when interpreting the query. "
                    + "Max length 50. No markdown, no extra text.";

            String raw;
            try {
                raw = groq.chat(prompt);
            } catch (Exception groqFailed) {
                raw = anthropic.callClaude(prompt, TOKENS);
            }
            List<Long> order = parseIdOrder(raw);

            if (!order.isEmpty()) {
                Map<Long, TrainingResponseDTO> byId = new HashMap<>();
                for (TrainingResponseDTO c : courses) {
                    if (c.getId() != null) {
                        byId.put(c.getId(), c);
                    }
                }
                List<TrainingResponseDTO> ranked = new ArrayList<>();
                Set<Long> seen = new LinkedHashSet<>();
                int rankScore = order.size() * 100;
                for (Long id : order) {
                    TrainingResponseDTO t = byId.get(id);
                    if (t != null && seen.add(id)) {
                        TrainingResponseDTO copy = copyForSearch(t, query, rankScore);
                        ranked.add(copy);
                        rankScore -= 5;
                    }
                }
                for (TrainingResponseDTO c : courses) {
                    if (c.getId() != null && !seen.contains(c.getId()) && matchesTokenHeuristic(c, q)) {
                        ranked.add(copyForSearch(c, query, tokenOverlapScore(c, q)));
                    }
                }
                if (!ranked.isEmpty()) {
                    return ranked;
                }
            }
        } catch (Exception ignored) {
        }
        return rankByLocalRelevance(query, courses);
    }

    private static String buildCatalogJsonForLlm(List<TrainingResponseDTO> courses) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TrainingResponseDTO c : courses) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("title", c.getTitle());
            m.put("description", c.getDescription());
            m.put("category", c.getCategory());
            m.put("level", c.getLevel());
            m.put("language", c.getLanguage());
            m.put("price", c.getPrice());
            m.put("averageRating", c.getAverageRating());
            m.put("reviewsCount", c.getReviewsCount());
            rows.add(m);
        }
        return MAPPER.writeValueAsString(rows);
    }

    private static String buildValidIdCsv(List<TrainingResponseDTO> courses) {
        return courses.stream()
                .map(TrainingResponseDTO::getId)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
    }

    private List<LearningPathStepDTO> parseLearningPath(String raw, List<TrainingResponseDTO> courses, String goal) {
        Map<Long, TrainingResponseDTO> byId = new HashMap<>();
        for (TrainingResponseDTO c : courses) {
            if (c.getId() != null) {
                byId.put(c.getId(), c);
            }
        }

        List<LearningPathStepDTO> out = new ArrayList<>();
        Set<Long> usedCourseIds = new LinkedHashSet<>();

        try {
            String json = extractJsonArray(raw.trim());
            JsonNode arr = MAPPER.readTree(json);
            if (!arr.isArray()) {
                return fallbackPath(goal, courses);
            }
            for (JsonNode n : arr) {
                long cid = n.path("courseId").asLong(0L);
                if (cid <= 0L || !byId.containsKey(cid) || usedCourseIds.contains(cid)) {
                    continue;
                }
                usedCourseIds.add(cid);

                LearningPathStepDTO step = new LearningPathStepDTO();
                step.setStep(n.path("step").asInt(out.size() + 1));
                step.setCourseId(cid);
                step.setTitle(n.path("title").asText(""));
                step.setReason(n.path("reason").asText(""));
                step.setEstimatedWeeks(n.path("estimatedWeeks").asInt(1));
                step.setCategory(n.path("category").asText(""));
                step.setLevel(n.path("level").asText(""));
                if (n.has("price") && n.get("price").isNumber()) {
                    step.setPrice(BigDecimal.valueOf(n.path("price").asDouble()));
                }
                step.setThumbnailUrl(n.path("thumbnailUrl").asText(""));

                TrainingResponseDTO src = byId.get(cid);
                if (src != null) {
                    if (step.getTitle() == null || step.getTitle().isBlank()) {
                        step.setTitle(src.getTitle());
                    }
                    if (step.getCategory() == null || step.getCategory().isBlank()) {
                        step.setCategory(src.getCategory());
                    }
                    if (step.getLevel() == null || step.getLevel().isBlank()) {
                        step.setLevel(src.getLevel());
                    }
                    if (step.getPrice() == null && src.getPrice() != null) {
                        step.setPrice(src.getPrice());
                    }
                    if (step.getThumbnailUrl() == null || step.getThumbnailUrl().isBlank()) {
                        step.setThumbnailUrl(src.getThumbnailUrl());
                    }
                }
                out.add(step);
            }
        } catch (Exception ignored) {
        }

        if (out.isEmpty()) {
            return fallbackPath(goal, courses);
        }

        for (int i = 0; i < out.size(); i++) {
            out.get(i).setStep(i + 1);
        }
        return out;
    }

    private List<LearningPathStepDTO> fallbackPath(String goal, List<TrainingResponseDTO> courses) {
        List<TrainingResponseDTO> sorted = new ArrayList<>(courses);
        sorted.sort(Comparator.comparing((TrainingResponseDTO c) -> c.getAverageRating() != null ? -c.getAverageRating() : 0.0));
        List<LearningPathStepDTO> out = new ArrayList<>();
        int step = 1;
        for (TrainingResponseDTO c : sorted.stream().limit(4).toList()) {
            if (c.getId() == null) {
                continue;
            }
            LearningPathStepDTO s = new LearningPathStepDTO();
            s.setStep(step++);
            s.setCourseId(c.getId());
            s.setTitle(c.getTitle());
            s.setReason("Fits goal: " + goal);
            s.setEstimatedWeeks(2);
            s.setCategory(c.getCategory());
            s.setLevel(c.getLevel());
            s.setPrice(c.getPrice());
            s.setThumbnailUrl(c.getThumbnailUrl());
            out.add(s);
        }
        return out;
    }

    private static List<Long> parseIdOrder(String raw) {
        List<Long> ids = new ArrayList<>();
        try {
            String stripped = stripMarkdownFences(raw);
            String json = extractJsonArray(stripped.trim());
            JsonNode arr = MAPPER.readTree(json);
            if (!arr.isArray()) {
                return ids;
            }
            for (JsonNode n : arr) {
                if (n.isNumber()) {
                    ids.add(n.asLong());
                } else if (n.isTextual()) {
                    String t = n.asText().trim();
                    if (!t.isBlank()) {
                        try {
                            String digits = t.replaceAll("[^0-9]", "");
                            if (!digits.isBlank()) {
                                ids.add(Long.parseLong(digits));
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } else if (n.isObject()) {
                    if (n.has("courseId")) {
                        ids.add(n.get("courseId").asLong());
                    } else if (n.has("id")) {
                        ids.add(n.get("id").asLong());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return ids;
    }

    private static String stripMarkdownFences(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl >= 0) {
                s = s.substring(firstNl + 1);
            } else {
                s = s.substring(3);
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3).trim();
            }
        }
        return s;
    }

    private static boolean matchesTokenHeuristic(TrainingResponseDTO c, String qLower) {
        if (qLower.isBlank()) {
            return true;
        }
        List<String> tokens = tokenize(qLower);
        if (tokens.isEmpty()) {
            return true;
        }
        return tokenOverlapScore(c, qLower) > 0;
    }

    private static List<String> tokenize(String qLower) {
        return Arrays.stream(qLower.split("\\s+"))
                .map(String::trim)
                .filter(t -> t.length() > 1)
                .filter(t -> !Set.of("in", "to", "a", "an", "the", "for", "of", "and", "or", "i", "want", "courses", "course")
                        .contains(t))
                .collect(Collectors.toList());
    }

    private static int tokenOverlapScore(TrainingResponseDTO c, String qLower) {
        List<String> tokens = tokenize(qLower);
        if (tokens.isEmpty()) {
            return 1;
        }
        String blob = (nz(c.getTitle()) + " " + nz(c.getDescription()) + " " + nz(c.getCategory()) + " " + nz(c.getLevel()) + " " + nz(c.getLanguage()))
                .toLowerCase(Locale.ROOT);
        int score = 0;
        for (String t : tokens) {
            if (blob.contains(t)) {
                score += 12;
            }
        }
        if (c.getAverageRating() != null) {
            score += (int) (c.getAverageRating() * 2);
        }
        return score;
    }

    private List<TrainingResponseDTO> rankByLocalRelevance(String query, List<TrainingResponseDTO> courses) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<TrainingResponseDTO> ranked = new ArrayList<>(courses);
        ranked.sort(Comparator.comparingInt((TrainingResponseDTO c) -> -tokenOverlapScore(c, q)));
        List<TrainingResponseDTO> nonzero = ranked.stream()
                .filter(c -> tokenOverlapScore(c, q) > 0)
                .collect(Collectors.toList());
        List<TrainingResponseDTO> source = nonzero.isEmpty() ? ranked : nonzero;
        return source.stream()
                .map(c -> copyForSearch(c, query, tokenOverlapScore(c, q)))
                .collect(Collectors.toList());
    }

    private static TrainingResponseDTO copyForSearch(TrainingResponseDTO c, String query, int relevanceScore) {
        TrainingResponseDTO copy = new TrainingResponseDTO();
        copy.setId(c.getId());
        copy.setTitle(c.getTitle());
        copy.setDescription(c.getDescription());
        copy.setCategory(c.getCategory());
        copy.setLevel(c.getLevel());
        copy.setPrice(c.getPrice());
        copy.setThumbnailUrl(c.getThumbnailUrl());
        copy.setLanguage(c.getLanguage());
        copy.setStatus(c.getStatus());
        copy.setContents(c.getContents());
        copy.setReviewsCount(c.getReviewsCount());
        copy.setAverageRating(c.getAverageRating());
        copy.setMatchReason("Matched query: " + query);
        copy.setRelevanceScore(relevanceScore);
        return copy;
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }

    private static String extractJsonArray(String s) {
        s = stripMarkdownFences(s);
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }
}

