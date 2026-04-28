package esprit.subscription.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import esprit.subscription.DTO.UserProfileDto;
import esprit.subscription.DTO.ai.TutorSalaryRecommendationDto;
import esprit.subscription.client.UserClient;
import esprit.subscription.entity.Subscription;
import esprit.subscription.repository.PaymentRecordRepository;
import esprit.subscription.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class TutorSalaryRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(TutorSalaryRecommendationService.class);

    private static final String SYSTEM_PROMPT = """
            Tu es un assistant RH pour Skillio. Réponds UNIQUEMENT en JSON valide.
            Clés attendues:
            - summary (string en français, 2-4 phrases)
            - confidence (nombre 0..100)
            - topFactors (tableau de 2 à 4 strings)
            - negotiationTips (tableau de 2 à 4 strings courts)
            N'invente pas de salaire, le salaire est déjà calculé côté moteur statistique.
            Explique le résultat à partir des métriques fournies.
            """;

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final UserClient userClient;
    private final GroqChatClient groqChatClient;
    private final ObjectMapper objectMapper;

    @Value("${skillio.tutor-salary.base-monthly:1200}")
    private double baseMonthly;

    @Value("${skillio.tutor-salary.min-monthly:700}")
    private double minMonthly;

    @Value("${skillio.tutor-salary.max-monthly:6000}")
    private double maxMonthly;

    @Value("${skillio.tutor-salary.weight-performance:0.50}")
    private double weightPerformance;

    @Value("${skillio.tutor-salary.weight-reliability:0.30}")
    private double weightReliability;

    @Value("${skillio.tutor-salary.weight-workload:0.20}")
    private double weightWorkload;

    @Value("${skillio.tutor-salary.groq.temperature:0.25}")
    private double groqTemperature;

    public TutorSalaryRecommendationService(SubscriptionRepository subscriptionRepository,
                                            PaymentRecordRepository paymentRecordRepository,
                                            UserClient userClient,
                                            GroqChatClient groqChatClient,
                                            ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.userClient = userClient;
        this.groqChatClient = groqChatClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public TutorSalaryRecommendationDto recommend(Long userId, Double targetMonthlyRevenue) {
        // 1. Get local subscriptions first (Our local source of truth for the relationship)
        List<Subscription> subs = subscriptionRepository.findByUserIdOrderByCreatedAtAsc(userId);
        if (subs.isEmpty()) {
            throw new IllegalArgumentException("No local subscription record found for userId=" + userId);
        }

        // 2. Identify role from our local database
        Subscription latestSub = subs.get(subs.size() - 1);
        String role = normalizeRole(latestSub.getUserRole());
        if (!"TUTEUR".equals(role)) {
            throw new IllegalArgumentException("User role '" + role + "' is not eligible for salary recommendation.");
        }

        // 3. Try to enrich with external profile (Rating), but default to 3.5 if fails (Resilience)
        UserProfileDto profile = tryEnrichProfile(userId);
        double rating = profile != null && profile.getAverageRating() != null ? profile.getAverageRating() : 3.5;
        boolean isSyntheticData = (profile == null);

        int monthsActive = estimateMonthsActive(subs);
        long successful = paymentRecordRepository.countSuccessfulByUserId(userId);
        long failed = paymentRecordRepository.countFailedByUserId(userId);
        double observedRevenue = safeDouble(paymentRecordRepository.sumSuccessfulAmountByUserId(userId));
        double revenueRef = targetMonthlyRevenue != null && targetMonthlyRevenue > 0
                ? targetMonthlyRevenue
                : observedRevenue;

        double performanceScore = round2((rating / 5.0) * 100.0);
        double reliabilityScore = round2(successful + failed > 0
                ? (successful * 100.0 / (successful + failed))
                : 50.0);
        double workloadScore = round2(clamp(
                (Math.min(monthsActive, 24) / 24.0) * 60.0 + (Math.min(successful, 30) / 30.0) * 40.0,
                0.0, 100.0));

        double overall = clamp(
                performanceScore * weightPerformance
                        + reliabilityScore * weightReliability
                        + workloadScore * weightWorkload,
                0.0, 100.0);

        // 0.5x to 1.75x scaling around base salary using score + revenue multiplier.
        double scoreMultiplier = 0.5 + (overall / 100.0) * 1.25;
        double revenueMultiplier = revenueRef > 0
                ? clamp(0.85 + (revenueRef / 4000.0), 0.85, 1.40)
                : 1.0;
        double recommended = clamp(baseMonthly * scoreMultiplier * revenueMultiplier, minMonthly, maxMonthly);
        double min = clamp(recommended * 0.9, minMonthly, maxMonthly);
        double max = clamp(recommended * 1.1, minMonthly, maxMonthly);
        
        int confidence = (int) Math.round(clamp(
                55 + (overall * 0.30) + (Math.min(monthsActive, 24) * 0.50),
                50, 98));
        
        if (isSyntheticData) {
            confidence = Math.max(50, confidence - 15);
        }

        List<String> defaultFactors = defaultTopFactors(rating, monthsActive, successful, failed, observedRevenue);
        if (isSyntheticData) {
            defaultFactors.add(0, "Note estimée : 3.5 (Profil externe introuvable)");
        }

        List<String> defaultTips = defaultTips(reliabilityScore, performanceScore);
        String summary = fallbackSummary(overall, recommended, rating, reliabilityScore);

        TutorSalaryRecommendationDto base = TutorSalaryRecommendationDto.builder()
                .userId(userId)
                .role(role)
                .rating(round2(rating))
                .targetMonthlyRevenue(targetMonthlyRevenue != null ? round2(targetMonthlyRevenue) : null)
                .observedMonthlyRevenue(round2(observedRevenue))
                .monthsActive(monthsActive)
                .successfulPayments((int) successful)
                .failedPayments((int) failed)
                .performanceScore(round2(performanceScore))
                .reliabilityScore(round2(reliabilityScore))
                .workloadScore(round2(workloadScore))
                .overallScore(round2(overall))
                .recommendedMonthlySalary(round2(recommended))
                .minMonthlySalary(round2(min))
                .maxMonthlySalary(round2(max))
                .currency("TND")
                .confidence(confidence)
                .topFactors(defaultFactors)
                .aiPowered(false)
                .summary(summary)
                .negotiationTips(defaultTips)
                .generatedAt(LocalDateTime.now())
                .modelSource(isSyntheticData ? "resilient-v1-virtual" : "deterministic-v1")
                .build();

        return withAiExplanation(base);
    }

    private TutorSalaryRecommendationDto withAiExplanation(TutorSalaryRecommendationDto base) {
        String payload = buildAiPayload(base);
        Optional<String> raw = groqChatClient.completeJson(
                SYSTEM_PROMPT, payload, clamp(groqTemperature, 0.0, 1.5));
        if (raw.isEmpty()) {
            return base;
        }
        try {
            JsonNode root = objectMapper.readTree(raw.get());
            String summary = textOrNull(root.path("summary"));
            Integer confidence = intOrNull(root.path("confidence"));
            List<String> topFactors = toTextList(root.path("topFactors"));
            List<String> tips = toTextList(root.path("negotiationTips"));
            return TutorSalaryRecommendationDto.builder()
                    .userId(base.getUserId())
                    .role(base.getRole())
                    .rating(base.getRating())
                    .targetMonthlyRevenue(base.getTargetMonthlyRevenue())
                    .observedMonthlyRevenue(base.getObservedMonthlyRevenue())
                    .monthsActive(base.getMonthsActive())
                    .successfulPayments(base.getSuccessfulPayments())
                    .failedPayments(base.getFailedPayments())
                    .performanceScore(base.getPerformanceScore())
                    .reliabilityScore(base.getReliabilityScore())
                    .workloadScore(base.getWorkloadScore())
                    .overallScore(base.getOverallScore())
                    .recommendedMonthlySalary(base.getRecommendedMonthlySalary())
                    .minMonthlySalary(base.getMinMonthlySalary())
                    .maxMonthlySalary(base.getMaxMonthlySalary())
                    .currency(base.getCurrency())
                    .confidence(confidence != null ? (int) clamp(confidence, 0, 100) : base.getConfidence())
                    .topFactors(!topFactors.isEmpty() ? topFactors : base.getTopFactors())
                    .aiPowered(summary != null || !tips.isEmpty() || !topFactors.isEmpty())
                    .summary(summary != null ? summary : base.getSummary())
                    .negotiationTips(!tips.isEmpty() ? tips : base.getNegotiationTips())
                    .generatedAt(base.getGeneratedAt())
                    .modelSource(summary != null || !tips.isEmpty() || !topFactors.isEmpty()
                            ? "deterministic-v1+groq-explainer"
                            : base.getModelSource())
                    .build();
        } catch (Exception e) {
            log.warn("Tutor salary AI parse failed: {}", e.getMessage());
            return base;
        }
    }

    private String buildAiPayload(TutorSalaryRecommendationDto base) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("userId", base.getUserId());
        root.put("role", base.getRole());
        root.put("rating", safeDouble(base.getRating()));
        root.put("targetMonthlyRevenue", safeDouble(base.getTargetMonthlyRevenue()));
        root.put("observedMonthlyRevenue", safeDouble(base.getObservedMonthlyRevenue()));
        root.put("monthsActive", base.getMonthsActive());
        root.put("successfulPayments", base.getSuccessfulPayments());
        root.put("failedPayments", base.getFailedPayments());
        root.put("performanceScore", base.getPerformanceScore());
        root.put("reliabilityScore", base.getReliabilityScore());
        root.put("workloadScore", base.getWorkloadScore());
        root.put("overallScore", base.getOverallScore());
        root.put("recommendedMonthlySalary", base.getRecommendedMonthlySalary());
        root.put("minMonthlySalary", base.getMinMonthlySalary());
        root.put("maxMonthlySalary", base.getMaxMonthlySalary());
        root.put("currency", base.getCurrency());
        root.put("confidence", base.getConfidence());
        root.put("today", LocalDate.now().toString());
        return root.toString();
    }

    private UserProfileDto tryEnrichProfile(Long userId) {
        try {
            return userClient.getUserById(userId);
        } catch (Exception e) {
            log.warn("Resilience Mode: External User Microservice unreachable for userId={}. Using defaults.", userId);
            return null;
        }
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "UNKNOWN";
        }
        String u = role.trim().toUpperCase();
        if ("TRAINER".equals(u) || "TUTOR".equals(u)) {
            return "TUTEUR";
        }
        return u;
    }

    private static int estimateMonthsActive(List<Subscription> subs) {
        return subs.stream()
                .filter(s -> s.getStartDate() != null)
                .min(Comparator.comparing(Subscription::getStartDate))
                .map(first -> {
                    long months = ChronoUnit.MONTHS.between(
                            first.getStartDate().toLocalDate().withDayOfMonth(1),
                            LocalDate.now().withDayOfMonth(1)
                    );
                    return (int) Math.max(1, months + 1);
                })
                .orElse(1);
    }

    private static List<String> defaultTopFactors(double rating, int monthsActive, long successful, long failed, double observedRevenue) {
        List<String> out = new ArrayList<>();
        out.add("Note moyenne: " + round2(rating) + "/5");
        out.add("Ancienneté active: " + monthsActive + " mois");
        out.add("Paiements réussis: " + successful + " (échecs: " + failed + ")");
        out.add("Revenu observé: " + round2(observedRevenue) + " TND");
        return out;
    }

    private static List<String> defaultTips(double reliabilityScore, double performanceScore) {
        List<String> tips = new ArrayList<>();
        tips.add("Réviser la recommandation tous les 3 mois selon les performances.");
        if (performanceScore >= 80) {
            tips.add("Prévoir un bonus de performance si la note reste au-dessus de 4.2/5.");
        } else {
            tips.add("Associer le salaire à un plan d'amélioration pédagogique mesurable.");
        }
        if (reliabilityScore < 70) {
            tips.add("Introduire une part variable liée à la fiabilité de paiement/activité.");
        }
        return tips;
    }

    private static String fallbackSummary(double overall, double salary, double rating, double reliability) {
        return "Recommandation calculée par score statistique ("
                + round2(overall)
                + "/100) avec une note moyenne de "
                + round2(rating)
                + "/5 et une fiabilité de "
                + round2(reliability)
                + "%. Salaire mensuel conseillé: "
                + round2(salary)
                + " TND.";
    }

    private static List<String> toTextList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        ArrayNode arr = (ArrayNode) node;
        for (JsonNode item : arr) {
            String txt = textOrNull(item);
            if (txt != null) {
                out.add(txt);
            }
        }
        return out;
    }

    private static Integer intOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong() || node.isFloat() || node.isDouble()) {
            return node.asInt();
        }
        return null;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String s = node.asText();
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private static double safeDouble(Double v) {
        return v == null || !Double.isFinite(v) ? 0.0 : v;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
