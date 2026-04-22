package esprit.subscription.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import esprit.subscription.DTO.RevenueForecastDto;
import esprit.subscription.entity.PricingPlan;
import esprit.subscription.entity.Subscription;
import esprit.subscription.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agrège le MRR réel et produit historique + prévisions simples pour le dashboard admin.
 */
@Service
public class RevenueIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(RevenueIntelligenceService.class);
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final double GROWTH_CAP = 0.15;
    private static final double GROWTH_FLOOR = -0.05;
    private static final double DEFAULT_MONTHLY_GROWTH = 0.015;

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    public RevenueIntelligenceService(SubscriptionRepository subscriptionRepository,
                                        SubscriptionService subscriptionService,
                                        ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Recalcul des agrégats « intelligence » (placeholder : tout est calculé à la volée).
     * Peut être étendu (cache, tâches batch, etc.).
     */
    public Instant refreshAggregates() {
        log.info("Revenue intelligence refresh requested — aggregates recomputed on next forecast read");
        return Instant.now();
    }

    @Transactional(readOnly = true)
    public RevenueForecastDto buildForecast() {
        List<Subscription> active = subscriptionRepository.findAllActiveWithPlans();
        double currentMrr = active.stream().mapToDouble(RevenueIntelligenceService::monthlyEquivalent).sum();
        currentMrr = round2(currentMrr);

        double monthlyRevenue = round2(subscriptionService.calculateMonthlyRevenue());
        double yearlyRevenue = round2(subscriptionService.calculateYearlyRevenue());

        YearMonth now = YearMonth.now();
        List<Double> historySeries = new ArrayList<>();
        List<String> historyMonths = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth ym = now.minusMonths(i);
            historyMonths.add(ym.format(MONTH_KEY));
            LocalDateTime endOfMonth = ym.atEndOfMonth().atTime(23, 59, 59);
            double mrrAtMonth = active.stream()
                    .filter(s -> !s.getCreatedAt().isAfter(endOfMonth))
                    .mapToDouble(RevenueIntelligenceService::monthlyEquivalent)
                    .sum();
            historySeries.add(round2(mrrAtMonth));
        }

        double g = estimateMonthlyGrowthRate(historySeries);
        double p3 = round2(currentMrr * Math.pow(1 + g, 3));
        double p6 = round2(currentMrr * Math.pow(1 + g, 6));
        double arr = round2(p6 * 12);

        List<Map<String, Object>> historyPoints = new ArrayList<>();
        for (int i = 0; i < historyMonths.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", historyMonths.get(i));
            row.put("mrr", historySeries.get(i));
            historyPoints.add(row);
        }

        List<Map<String, Object>> forecastPoints = new ArrayList<>();
        for (int k = 1; k <= 6; k++) {
            YearMonth ym = now.plusMonths(k);
            double mrr = round2(currentMrr * Math.pow(1 + g, k));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", ym.format(MONTH_KEY));
            row.put("mrr", mrr);
            row.put("lower", round2(mrr * 0.9));
            row.put("upper", round2(mrr * 1.1));
            forecastPoints.add(row);
        }

        String historyJson = "[]";
        String forecastJson = "[]";
        try {
            historyJson = objectMapper.writeValueAsString(historyPoints);
            forecastJson = objectMapper.writeValueAsString(forecastPoints);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize MRR JSON", e);
        }

        return RevenueForecastDto.builder()
                .currentMrr(currentMrr)
                .monthlyRevenue(monthlyRevenue)
                .yearlyRevenue(yearlyRevenue)
                .projected3Month(p3)
                .projected6Month(p6)
                .projectedArr(arr)
                .historyJson(historyJson)
                .forecastJson(forecastJson)
                .generatedAt(Instant.now().toString())
                .build();
    }

    private static double estimateMonthlyGrowthRate(List<Double> historyChrono) {
        if (historyChrono == null || historyChrono.size() < 2) {
            return DEFAULT_MONTHLY_GROWTH;
        }
        double first = historyChrono.get(0);
        double last = historyChrono.get(historyChrono.size() - 1);
        int span = historyChrono.size() - 1;
        if (first <= 0 || last <= 0 || span <= 0) {
            return DEFAULT_MONTHLY_GROWTH;
        }
        double raw = Math.pow(last / first, 1.0 / span) - 1.0;
        if (Double.isNaN(raw) || Double.isInfinite(raw)) {
            return DEFAULT_MONTHLY_GROWTH;
        }
        return Math.max(GROWTH_FLOOR, Math.min(GROWTH_CAP, raw));
    }

    private static double monthlyEquivalent(Subscription s) {
        PricingPlan p = s.getPricingPlan();
        if (p == null) {
            return 0;
        }
        String cycle = s.getBillingCycle();
        if (cycle != null && cycle.equalsIgnoreCase("YEARLY")) {
            Double y = p.getYearlyPrice();
            return y != null ? y / 12.0 : 0;
        }
        Double m = p.getMonthlyPrice();
        return m != null ? m : 0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
