package tn.esprit.training.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import tn.esprit.training.dto.ReviewsAnalyticsDTO;
import tn.esprit.training.repository.AvisRepository;
import tn.esprit.training.repository.TrainingRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReviewsAnalyticsService {

    @PersistenceContext
    private EntityManager em;

    private final AvisRepository avisRepository;
    private final TrainingRepository trainingRepository;

    public ReviewsAnalyticsService(AvisRepository avisRepository, TrainingRepository trainingRepository) {
        this.avisRepository = avisRepository;
        this.trainingRepository = trainingRepository;
    }

    public ReviewsAnalyticsDTO getOverview() {
        ReviewsAnalyticsDTO dto = new ReviewsAnalyticsDTO();

        try {
            long totalReviews = avisRepository.count();
            dto.setTotalReviews(totalReviews);
        } catch (Exception e) {
            dto.setTotalReviews(0L);
        }

        try {
            Double avgRating = avisRepository.averageRatingGlobal();
            dto.setGlobalAverageRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0);
        } catch (Exception e) {
            dto.setGlobalAverageRating(0.0);
        }

        dto.setRatingDistribution(getRatingDistribution());
        dto.setTopByAvgRating(getTopByAvgRating());
        dto.setTopByReviewsCount(getTopByReviewsCount());
        return dto;
    }

    private List<ReviewsAnalyticsDTO.RatingDistributionItem> getRatingDistribution() {
        List<ReviewsAnalyticsDTO.RatingDistributionItem> result = new ArrayList<>();
        try {
            for (int rating = 1; rating <= 5; rating++) {
                Long count = avisRepository.countByRating(rating);
                result.add(new ReviewsAnalyticsDTO.RatingDistributionItem(rating, count != null ? count : 0L));
            }
        } catch (Exception e) {
            for (int rating = 1; rating <= 5; rating++) {
                result.add(new ReviewsAnalyticsDTO.RatingDistributionItem(rating, 0L));
            }
        }
        return result;
    }

    private List<ReviewsAnalyticsDTO.TopRatingItem> getTopByAvgRating() {
        List<ReviewsAnalyticsDTO.TopRatingItem> result = new ArrayList<>();
        try {
            List<Object[]> topRated = em.createNativeQuery("""
                SELECT t.id, t.title, AVG(a.rating) as avg_rating, COUNT(a.id) as reviews_count
                FROM training t
                LEFT JOIN avis a ON a.training_id = t.id
                WHERE a.rating IS NOT NULL
                GROUP BY t.id, t.title
                HAVING COUNT(a.id) > 0
                ORDER BY avg_rating DESC, reviews_count DESC
                LIMIT 5
            """).getResultList();

            for (Object[] row : topRated) {
                ReviewsAnalyticsDTO.TopRatingItem item = new ReviewsAnalyticsDTO.TopRatingItem();
                item.setId(((Number) row[0]).longValue());
                item.setTitle((String) row[1]);
                item.setAvgRating(row[2] != null ? Math.round(((Number) row[2]).doubleValue() * 10.0) / 10.0 : 0.0);
                item.setReviewsCount(((Number) row[3]).longValue());
                result.add(item);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private List<ReviewsAnalyticsDTO.TopReviewsItem> getTopByReviewsCount() {
        List<ReviewsAnalyticsDTO.TopReviewsItem> result = new ArrayList<>();
        try {
            List<Object[]> topReviewed = em.createNativeQuery("""
                SELECT t.id, t.title, COUNT(a.id) as reviews_count, AVG(a.rating) as avg_rating
                FROM training t
                LEFT JOIN avis a ON a.training_id = t.id
                GROUP BY t.id, t.title
                HAVING COUNT(a.id) > 0
                ORDER BY reviews_count DESC, avg_rating DESC
                LIMIT 5
            """).getResultList();

            for (Object[] row : topReviewed) {
                ReviewsAnalyticsDTO.TopReviewsItem item = new ReviewsAnalyticsDTO.TopReviewsItem();
                item.setId(((Number) row[0]).longValue());
                item.setTitle((String) row[1]);
                item.setReviewsCount(((Number) row[2]).longValue());
                item.setAvgRating(row[3] != null ? Math.round(((Number) row[3]).doubleValue() * 10.0) / 10.0 : 0.0);
                result.add(item);
            }
        } catch (Exception ignored) {
        }
        return result;
    }
}

