package tn.esprit.training.analytics;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.training.analytics.dto.AnalyticsOverviewDTO;
import tn.esprit.training.analytics.dto.TrainingAnalyticsDTO;
import tn.esprit.training.entity.Training;
import tn.esprit.training.repository.AvisRepository;
import tn.esprit.training.repository.TrainingContentRepository;
import tn.esprit.training.repository.TrainingRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    @PersistenceContext
    private EntityManager em;

    private final TrainingRepository trainingRepository;
    private final AvisRepository avisRepository;
    private final TrainingContentRepository contentRepository;

    public AnalyticsService(
            TrainingRepository trainingRepository,
            AvisRepository avisRepository,
            TrainingContentRepository contentRepository
    ) {
        this.trainingRepository = trainingRepository;
        this.avisRepository = avisRepository;
        this.contentRepository = contentRepository;
    }

    public AnalyticsOverviewDTO overview() {
        AnalyticsOverviewDTO dto = new AnalyticsOverviewDTO();
        dto.totalTrainings = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM training").getSingleResult()).longValue();
        dto.totalContents = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM training_content").getSingleResult()).longValue();
        dto.totalReviews = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM avis").getSingleResult()).longValue();
        Number avgRating = (Number) em.createNativeQuery("SELECT COALESCE(AVG(rating),0) FROM avis").getSingleResult();
        dto.globalAverageRating = avgRating == null ? 0.0 : Math.round(avgRating.doubleValue() * 10.0) / 10.0;
        dto.globalAvgRating = dto.globalAverageRating;
        dto.freeTrainings = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM training WHERE price IS NULL OR price = 0").getSingleResult()).longValue();
        dto.paidTrainings = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM training WHERE price > 0").getSingleResult()).longValue();
        log.info("[Analytics] totals: trainings={}, contents={}, reviews={}, avg={}, free={}, paid={}",
                dto.totalTrainings, dto.totalContents, dto.totalReviews, dto.globalAverageRating, dto.freeTrainings, dto.paidTrainings);

        List<Object[]> claimsRows = em.createNativeQuery("""
            SELECT t.id, t.title, COUNT(*) AS claims
            FROM training t
            JOIN training_content c ON c.training_id = t.id
            JOIN preview_progress pp ON pp.content_id = c.id
            GROUP BY t.id, t.title
            ORDER BY claims DESC
            LIMIT 5
        """).getResultList();
        dto.topByClaims = new ArrayList<>();
        for (Object[] r : claimsRows) {
            AnalyticsOverviewDTO.TopClaimsItem item = new AnalyticsOverviewDTO.TopClaimsItem();
            item.id = ((Number) r[0]).longValue();
            item.title = (String) r[1];
            item.claimsCount = ((Number) r[2]).longValue();
            dto.topByClaims.add(item);
        }

        List<Object[]> ratingRows = em.createNativeQuery("""
            SELECT t.id, t.title, AVG(a.rating) AS avg_rating, COUNT(a.id) AS reviews
            FROM training t
            JOIN avis a ON a.training_id = t.id
            GROUP BY t.id, t.title
            ORDER BY avg_rating DESC, reviews DESC
            LIMIT 5
        """).getResultList();
        dto.topByRating = new ArrayList<>();
        for (Object[] r : ratingRows) {
            AnalyticsOverviewDTO.TopRatingItem item = new AnalyticsOverviewDTO.TopRatingItem();
            item.id = ((Number) r[0]).longValue();
            item.title = (String) r[1];
            item.avgRating = r[2] == null ? 0.0 : ((Number) r[2]).doubleValue();
            item.reviewsCount = ((Number) r[3]).longValue();
            dto.topByRating.add(item);
        }

        try {
            List<Object[]> dailyClaims = em.createNativeQuery("""
                SELECT DATE(pp.claimed_at) as d, COUNT(*)
                FROM preview_progress pp
                WHERE pp.claimed_at >= DATE_SUB(CURDATE(), INTERVAL 14 DAY)
                GROUP BY DATE(pp.claimed_at)
                ORDER BY d
            """).getResultList();
            dto.claimsDaily = dailyClaims.stream().map(r -> {
                AnalyticsOverviewDTO.DailyCount dc = new AnalyticsOverviewDTO.DailyCount();
                dc.date = r[0].toString();
                dc.count = ((Number) r[1]).longValue();
                return dc;
            }).collect(Collectors.toList());
        } catch (Exception ignore) {
            dto.claimsDaily = List.of();
            log.warn("[Analytics] claimsDaily query failed or column missing, skipping.");
        }

        try {
            List<Object[]> dailyReviews = em.createNativeQuery("""
                SELECT DATE(a.created_at) as d, COUNT(*)
                FROM avis a
                WHERE a.created_at >= DATE_SUB(CURDATE(), INTERVAL 14 DAY)
                GROUP BY DATE(a.created_at)
                ORDER BY d
            """).getResultList();
            dto.reviewsDaily = dailyReviews.stream().map(r -> {
                AnalyticsOverviewDTO.DailyCount dc = new AnalyticsOverviewDTO.DailyCount();
                dc.date = r[0].toString();
                dc.count = ((Number) r[1]).longValue();
                return dc;
            }).collect(Collectors.toList());
        } catch (Exception ignore) {
            dto.reviewsDaily = List.of();
            log.warn("[Analytics] reviewsDaily query failed or column missing, skipping.");
        }

        return dto;
    }

    public TrainingAnalyticsDTO analyticsForTraining(Long trainingId) {
        Training t = trainingRepository.findById(trainingId).orElse(null);
        if (t == null) {
            TrainingAnalyticsDTO none = new TrainingAnalyticsDTO();
            none.trainingId = trainingId;
            return none;
        }
        Double avg = avisRepository.averageRatingByTrainingId(trainingId);
        long reviews = avisRepository.countByTraining_Id(trainingId);
        long totalContents = contentRepository.countByTrainingIdNative(trainingId);
        List<Long> contentIds = contentRepository.findIdsByTrainingIdNative(trainingId);
        long claimed = 0;
        if (!contentIds.isEmpty()) {
            String inClause = contentIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            String sql = "SELECT COUNT(DISTINCT pp.content_id) FROM preview_progress pp WHERE pp.content_id IN (" + inClause + ")";
            Number n = (Number) em.createNativeQuery(sql).getSingleResult();
            claimed = n.longValue();
        }
        TrainingAnalyticsDTO dto = new TrainingAnalyticsDTO();
        dto.trainingId = trainingId;
        dto.title = t.getTitle();
        dto.avgRating = avg == null ? 0.0 : Math.round(avg * 10.0) / 10.0;
        dto.reviewsCount = reviews;
        dto.totalContents = totalContents;
        dto.claimedContents = claimed;
        dto.completionRate = totalContents == 0 ? 0.0 : Math.round(((claimed * 100.0) / totalContents) * 10.0) / 10.0;
        return dto;
    }
}

