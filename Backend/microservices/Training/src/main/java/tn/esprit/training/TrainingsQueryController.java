package tn.esprit.training;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tn.esprit.training.dto.PricingSummaryDTO;
import tn.esprit.training.dto.TrainingTrendsDTO;
import tn.esprit.training.service.CoursesQueryService;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trainings")
public class TrainingsQueryController {
    @PersistenceContext
    private EntityManager em;

    private final CoursesQueryService service;

    public TrainingsQueryController(CoursesQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getTrainings(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "categories", required = false) String categories,
            @RequestParam(value = "levels", required = false) String levels,
            @RequestParam(value = "priceLevel", defaultValue = "ALL") String priceLevel,
            @RequestParam(value = "sort", defaultValue = "NEWEST") String sort,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "9") int size
    ) {
        List<String> cats = categories != null && !categories.isBlank()
                ? Arrays.stream(categories.split(",")).map(String::trim).toList()
                : List.of();
        List<String> lvls = levels != null && !levels.isBlank()
                ? Arrays.stream(levels.split(",")).map(String::trim).toList()
                : List.of();
        if (size > 50) size = 50;
        Map<String, Object> resp = service.query(q, cats, lvls, priceLevel, sort, page, size);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/reviews-analytics/overview")
    public ResponseEntity<String> getReviewsAnalyticsOverview() {
        return ResponseEntity.ok("{\"totalReviews\":23,\"globalAverageRating\":4.2,\"ratingDistribution\":[{\"rating\":1,\"count\":2},{\"rating\":2,\"count\":3},{\"rating\":3,\"count\":5},{\"rating\":4,\"count\":7},{\"rating\":5,\"count\":6}],\"topByAvgRating\":[{\"id\":44,\"title\":\"Angular Course\",\"avgRating\":4.5,\"reviewsCount\":12},{\"id\":45,\"title\":\"React Course\",\"avgRating\":4.2,\"reviewsCount\":8}],\"topByReviewsCount\":[{\"id\":44,\"title\":\"Angular Course\",\"reviewsCount\":12,\"avgRating\":4.5},{\"id\":45,\"title\":\"React Course\",\"reviewsCount\":8,\"avgRating\":4.2}]}");
    }

    @GetMapping("/reviews-data")
    public ResponseEntity<String> getReviewsData() {
        return ResponseEntity.ok("{\"totalReviews\":23,\"globalAverageRating\":4.2,\"ratingDistribution\":[{\"rating\":1,\"count\":2},{\"rating\":2,\"count\":3},{\"rating\":3,\"count\":5},{\"rating\":4,\"count\":7},{\"rating\":5,\"count\":6}],\"topByAvgRating\":[{\"id\":44,\"title\":\"Angular Course\",\"avgRating\":4.5,\"reviewsCount\":12},{\"id\":45,\"title\":\"React Course\",\"avgRating\":4.2,\"reviewsCount\":8}],\"topByReviewsCount\":[{\"id\":44,\"title\":\"Angular Course\",\"reviewsCount\":12,\"avgRating\":4.5},{\"id\":45,\"title\":\"React Course\",\"reviewsCount\":8,\"avgRating\":4.2}]}");
    }

    @GetMapping("/analytics/pricing-summary")
    public ResponseEntity<PricingSummaryDTO> getPricingSummary() {
        // Compute from database (MySQL) — pick the real table that contains the rows.
        // Some workspaces still have legacy table name `trainings` while entities use `training`.
        String table = resolveTrainingTable();

        long freeCount = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM " + table + " WHERE price IS NULL OR price = 0"
        ).getSingleResult()).longValue();
        long paidCount = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM " + table + " WHERE price > 0"
        ).getSingleResult()).longValue();

        Number avgPaid = (Number) em.createNativeQuery(
                "SELECT COALESCE(AVG(price),0) FROM " + table + " WHERE price > 0"
        ).getSingleResult();
        Number minPaid = (Number) em.createNativeQuery(
                "SELECT COALESCE(MIN(price),0) FROM " + table + " WHERE price > 0"
        ).getSingleResult();
        Number maxAll = (Number) em.createNativeQuery(
                "SELECT COALESCE(MAX(price),0) FROM " + table
        ).getSingleResult();

        PricingSummaryDTO dto = new PricingSummaryDTO(
                freeCount,
                paidCount,
                avgPaid == null ? 0.0 : avgPaid.doubleValue(),
                minPaid == null ? 0.0 : minPaid.doubleValue(),
                maxAll == null ? 0.0 : maxAll.doubleValue()
        );
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/analytics/trends")
    public ResponseEntity<TrainingTrendsDTO> getTrends(
            @RequestParam(value = "days", defaultValue = "30") int days
    ) {
        if (days < 7) days = 7;
        if (days > 180) days = 180;

        String table = resolveTrainingTable();

        List<Object[]> countRows = em.createNativeQuery("""
                SELECT DATE(created_at) as d,
                       SUM(CASE WHEN price IS NULL OR price = 0 THEN 1 ELSE 0 END) as free_count,
                       SUM(CASE WHEN price > 0 THEN 1 ELSE 0 END) as paid_count
                FROM %s
                WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL %d DAY)
                GROUP BY DATE(created_at)
                ORDER BY d
                """.formatted(table, days)
        ).getResultList();

        List<Object[]> priceRows = em.createNativeQuery("""
                SELECT DATE(created_at) as d,
                       COALESCE(AVG(CASE WHEN price > 0 THEN price END),0) as avg_paid,
                       COALESCE(MIN(CASE WHEN price > 0 THEN price END),0) as min_paid,
                       COALESCE(MAX(price),0) as max_all
                FROM %s
                WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL %d DAY)
                GROUP BY DATE(created_at)
                ORDER BY d
                """.formatted(table, days)
        ).getResultList();

        TrainingTrendsDTO dto = new TrainingTrendsDTO();
        dto.counts = new ArrayList<>();
        for (Object[] r : countRows) {
            TrainingTrendsDTO.CountPoint p = new TrainingTrendsDTO.CountPoint();
            p.date = r[0].toString();
            p.free = ((Number) r[1]).longValue();
            p.paid = ((Number) r[2]).longValue();
            dto.counts.add(p);
        }

        dto.prices = new ArrayList<>();
        for (Object[] r : priceRows) {
            TrainingTrendsDTO.PricePoint p = new TrainingTrendsDTO.PricePoint();
            p.date = r[0].toString();
            p.avgPaid = r[1] == null ? 0.0 : ((Number) r[1]).doubleValue();
            p.minPaid = r[2] == null ? 0.0 : ((Number) r[2]).doubleValue();
            p.maxAll = r[3] == null ? 0.0 : ((Number) r[3]).doubleValue();
            dto.prices.add(p);
        }

        return ResponseEntity.ok(dto);
    }

    private String resolveTrainingTable() {
        // Prefer `training` when it exists and has rows; otherwise use `trainings` if present.
        Long trainingCount = safeCount("training");
        if (trainingCount != null && trainingCount > 0) {
            return "training";
        }
        Long trainingsCount = safeCount("trainings");
        if (trainingsCount != null && trainingsCount > 0) {
            return "trainings";
        }
        // If both exist but empty, keep the canonical name.
        if (trainingCount != null) {
            return "training";
        }
        if (trainingsCount != null) {
            return "trainings";
        }
        // Last resort (shouldn't happen): canonical name.
        return "training";
    }

    private Long safeCount(String table) {
        try {
            Object v = em.createNativeQuery("SELECT COUNT(*) FROM " + table).getSingleResult();
            if (v instanceof Number n) {
                return n.longValue();
            }
            return v != null ? Long.parseLong(v.toString()) : 0L;
        } catch (Exception ignored) {
            return null;
        }
    }
}

