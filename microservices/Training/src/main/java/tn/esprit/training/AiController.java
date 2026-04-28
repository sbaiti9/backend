package tn.esprit.training;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.training.service.AiRecommendationService;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiRecommendationService ai;

    public AiController(AiRecommendationService ai) {
        this.ai = ai;
    }

    @PostMapping("/recommend")
    public ResponseEntity<Map<String, Object>> recommend() throws Exception {
        String raw = ai.recommend();
        return ResponseEntity.ok(Map.of("raw", raw));
    }

    @PostMapping("/recommend/items")
    public ResponseEntity<Map<String, Object>> recommendItems() throws Exception {
        var items = ai.recommendItems();
        return ResponseEntity.ok(Map.of("items", items));
    }
}

