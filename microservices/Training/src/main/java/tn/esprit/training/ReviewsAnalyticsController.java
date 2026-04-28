package tn.esprit.training;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.training.dto.ReviewsAnalyticsDTO;
import tn.esprit.training.service.ReviewsAnalyticsService;

@RestController
@RequestMapping("/api/reviews-analytics")
public class ReviewsAnalyticsController {

    private final ReviewsAnalyticsService service;

    public ReviewsAnalyticsController(ReviewsAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ResponseEntity<ReviewsAnalyticsDTO> getOverview() {
        ReviewsAnalyticsDTO overview = service.getOverview();
        return ResponseEntity.ok(overview);
    }
}

