package tn.esprit.training;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.training.dto.AvisCreateDTO;
import tn.esprit.training.dto.AvisDTO;
import tn.esprit.training.service.ReviewService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/reviews")
public class ReviewController {
    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/training/{trainingId}")
    public ResponseEntity<List<AvisDTO>> getByTraining(@PathVariable Long trainingId) {
        return ResponseEntity.ok(reviewService.getReviewsByTraining(trainingId));
    }

    @GetMapping("/courses/{courseId}/reviews")
    public ResponseEntity<Map<String, Object>> getPaged(
            @PathVariable("courseId") Long courseId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "sort", defaultValue = "newest") String sort
    ) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 10;
        Map<String, Object> resp = reviewService.getReviewsPaged(courseId, page, pageSize, sort);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/training/{trainingId}")
    public ResponseEntity<AvisDTO> add(@PathVariable Long trainingId, @Valid @RequestBody AvisCreateDTO req) {
        return ResponseEntity.ok(reviewService.addReview(trainingId, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reviewService.deleteReview(id);
        return ResponseEntity.noContent().build();
    }
}

