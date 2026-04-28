package tn.esprit.training.chat;

import org.springframework.stereotype.Service;
import tn.esprit.training.chat.dto.ChatResponse;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ChatSimpleService {
    private final TrainingRepositoryJdbc trainingJdbc;
    private final AvisRepositoryJdbc avisJdbc;

    public ChatSimpleService(TrainingRepositoryJdbc trainingJdbc, AvisRepositoryJdbc avisJdbc) {
        this.trainingJdbc = trainingJdbc;
        this.avisJdbc = avisJdbc;
    }

    public ChatResponse answer(String message) {
        IntentDetector.Result det = IntentDetector.detect(message);
        Intent intent = det.intent;
        List<Map<String, Object>> items = new ArrayList<>();
        String answer;
        switch (intent) {
            case TOP_RATED -> {
                items = trainingJdbc.topRated();
                answer = buildTopRated(items);
            }
            case MOST_REVIEWED -> {
                items = trainingJdbc.mostReviewed();
                answer = buildMostReviewed(items);
            }
            case CHEAPEST -> {
                items = trainingJdbc.cheapest();
                answer = buildPriceList("Cheapest courses:", items);
            }
            case MOST_EXPENSIVE -> {
                items = trainingJdbc.mostExpensive();
                answer = buildPriceList("Most expensive courses:", items);
            }
            case NO_REVIEWS -> {
                items = trainingJdbc.noReviews();
                answer = buildNoReviews(items);
            }
            case SEARCH_COURSE -> {
                items = trainingJdbc.searchCourses(det.param);
                answer = buildSearch(det.param, items);
            }
            case REVIEWS_FOR_COURSE -> {
                items = avisJdbc.reviewsForTitleLike(det.param);
                answer = buildReviews(det.param, items);
            }
            default -> answer = helpText();
        }
        ChatResponse resp = new ChatResponse();
        resp.setIntent(intent.name());
        resp.setItems(items);
        resp.setAnswer(answer);
        return resp;
    }

    private String helpText() {
        return """
        I can answer questions about courses and reviews (training + avis).

        Try:
        - top rated courses
        - most reviewed courses
        - cheapest courses
        - most expensive courses
        - courses without reviews
        - search <keyword>   (example: search java)
        - reviews of <course> (example: reviews of spring)
        """;
    }

    private String buildTopRated(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "No results found.";
        DecimalFormat df = new DecimalFormat("0.0");
        StringBuilder sb = new StringBuilder("Top rated courses: ");
        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            double avg = toDouble(r.get("avg_rating"));
            long cnt = toLong(r.get("nb_reviews"));
            sb.append(r.get("title")).append(" (").append(df.format(avg)).append("/5, ").append(cnt).append(" reviews)");
            if (i < rows.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    private String buildMostReviewed(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "No results found.";
        StringBuilder sb = new StringBuilder("Most reviewed courses: ");
        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            long cnt = toLong(r.get("nb_reviews"));
            sb.append(r.get("title")).append(" (").append(cnt).append(" reviews)");
            if (i < rows.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    private String buildPriceList(String prefix, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "No results found.";
        StringBuilder sb = new StringBuilder(prefix).append(" ");
        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            sb.append(r.get("title")).append(" (").append(r.get("price")).append(")");
            if (i < rows.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    private String buildNoReviews(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "No results found.";
        StringBuilder sb = new StringBuilder("Courses with no reviews: ");
        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            sb.append(r.get("title"));
            if (i < rows.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    private String buildSearch(String q, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "No results found.";
        StringBuilder sb = new StringBuilder();
        sb.append("Search results for '").append(q).append("': ");
        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            sb.append(r.get("title"));
            if (i < rows.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    private String buildReviews(String q, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "No results found.";
        StringBuilder sb = new StringBuilder();
        sb.append("Recent reviews for '").append(q).append("': ");
        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            sb.append(r.get("rating")).append(" - ").append(r.get("comment"));
            if (i < rows.size() - 1) sb.append(" | ");
        }
        return sb.toString();
    }

    private double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(o.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private long toLong(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (Exception e) {
            return 0;
        }
    }
}

