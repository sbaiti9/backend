package esprit.subscription.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import esprit.subscription.DTO.CompanyDto;
import esprit.subscription.DTO.PlanRecommendationDto;
import esprit.subscription.DTO.UserProfileDto;
import esprit.subscription.client.EntrepriseClient;
import esprit.subscription.client.UserClient;
import esprit.subscription.entity.PricingPlan;
import esprit.subscription.entity.Subscription;
import esprit.subscription.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Groq-based plan recommendation using user profile, company (entreprise), catalog, and stats baseline.
 */
@Service
public class PlanAdvisorAiService {

    private static final Logger log = LoggerFactory.getLogger(PlanAdvisorAiService.class);

    private static final String SYSTEM = """
            Tu es le conseiller d'abonnement Skillio. Tu dois UNIQUEMENT répondre par un objet JSON valide, sans markdown.
            Clés obligatoires :
            - recommendedPlanId (nombre) : l'id du plan choisi parmi la liste fournie.
            - reason (string, en français) : 2 à 4 phrases, ton professionnel, personnalisé au profil.
            - confidence (nombre 0 à 100) : ta confiance dans cette recommandation.
            - preferredBillingCycle : soit "MONTHLY", soit "YEARLY", soit null si tu n'as pas d'avis.
            Règles : choisir exactement un planId présent dans activePlans. Tenir compte du rôle (étudiant vs entreprise),
            du budget (prix mensuel/annuel), des fonctionnalités, et de l'abonnement actuel s'il existe.
            Si le profil est incomplet, reste prudent et explique-le dans reason tout en choisissant le plan le plus cohérent.
            """;

    private final GroqChatClient groqChatClient;
    private final UserClient userClient;
    private final EntrepriseClient entrepriseClient;
    private final PricingPlanService pricingPlanService;
    private final SubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;

    /** Same Groq endpoint/key/model as unlock-chat ({@code groq.api.*}); only temperature is feature-specific. */
    @Value("${skillio.plan-advisor.groq.temperature:0.35}")
    private double planAdvisorGroqTemperature;

    public PlanAdvisorAiService(GroqChatClient groqChatClient,
                                UserClient userClient,
                                EntrepriseClient entrepriseClient,
                                PricingPlanService pricingPlanService,
                                SubscriptionRepository subscriptionRepository,
                                ObjectMapper objectMapper) {
        this.groqChatClient = groqChatClient;
        this.userClient = userClient;
        this.entrepriseClient = entrepriseClient;
        this.pricingPlanService = pricingPlanService;
        this.subscriptionRepository = subscriptionRepository;
        this.objectMapper = objectMapper;
    }

    public PlanRecommendationDto advise(String userRole, Long userId, PlanRecommendationDto baseline) {
        String role = normalizeUserRole(userRole);
        double temp = Math.max(0.0, Math.min(1.5, planAdvisorGroqTemperature));
        Optional<String> raw = groqChatClient.completeJson(SYSTEM, buildUserPayload(role, userId, baseline), temp);
        if (raw.isEmpty()) {
            return copyBaseline(baseline, false, null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(raw.get());
            long planId = root.path("recommendedPlanId").asLong(0);
            String reason = root.path("reason").asText(null);
            double conf = root.path("confidence").asDouble(Double.NaN);
            String cycle = textOrNull(root.path("preferredBillingCycle"));

            if (planId <= 0 || reason == null || reason.isBlank()) {
                return copyBaseline(baseline, false, null, null);
            }

            Optional<PricingPlan> planOpt = pricingPlanService.findById(planId);
            if (planOpt.isEmpty()) {
                log.warn("AI recommended unknown planId={}", planId);
                return copyBaseline(baseline, false, null, null);
            }
            PricingPlan plan = planOpt.get();

            int confidencePct = Double.isFinite(conf)
                    ? (int) Math.round(Math.max(0, Math.min(100, conf)))
                    : 75;

            PlanRecommendationDto stats = resolveStatsForPlan(role, planId, plan, baseline);
            return PlanRecommendationDto.builder()
                    .planId(planId)
                    .planName(plan.getName())
                    .reason(reason.trim())
                    .activeUserCount(stats.getActiveUserCount())
                    .adoptionRate(stats.getAdoptionRate())
                    .mostPopular(stats.isMostPopular())
                    .bestValue(stats.isBestValue())
                    .aiPowered(true)
                    .confidenceScore(confidencePct)
                    .billingCycleHint(cycle)
                    .build();
        } catch (Exception e) {
            log.warn("Plan advisor AI parse failed: {}", e.getMessage());
            return copyBaseline(baseline, false, null, null);
        }
    }

    private PlanRecommendationDto copyBaseline(PlanRecommendationDto baseline, boolean ai,
                                               Integer confidence, String cycleHint) {
        return PlanRecommendationDto.builder()
                .planId(baseline.getPlanId())
                .planName(baseline.getPlanName())
                .reason(baseline.getReason())
                .activeUserCount(baseline.getActiveUserCount())
                .adoptionRate(baseline.getAdoptionRate())
                .mostPopular(baseline.isMostPopular())
                .bestValue(baseline.isBestValue())
                .aiPowered(ai)
                .confidenceScore(confidence)
                .billingCycleHint(cycleHint)
                .build();
    }

    /**
     * Recompute adoption-style fields for the chosen plan; reuse baseline when same plan as stats recommendation.
     */
    private PlanRecommendationDto resolveStatsForPlan(String role, Long planId, PricingPlan plan,
                                                      PlanRecommendationDto baseline) {
        if (baseline.getPlanId() != null && baseline.getPlanId().equals(planId)) {
            return baseline;
        }

        long totalForRole = subscriptionRepository.countActiveByUserRoleNormalized(role);
        List<Object[]> rows = subscriptionRepository.countActiveByPlanForRole(role);
        if (rows.isEmpty() || totalForRole == 0) {
            rows = subscriptionRepository.countActivePlansWithIds();
            totalForRole = subscriptionRepository.countByStatus("ACTIVE");
        }

        long cnt = 0;
        boolean found = false;
        for (Object[] row : rows) {
            Long pid = (Long) row[0];
            if (pid != null && pid.equals(planId)) {
                cnt = (Long) row[2];
                found = true;
                break;
            }
        }
        if (!found && !rows.isEmpty()) {
            Object[] top = rows.get(0);
            if (planId.equals((Long) top[0])) {
                cnt = (Long) top[2];
                found = true;
            }
        }

        double adoption = (found && totalForRole > 0) ? (cnt * 100.0 / totalForRole) : 0.0;
        boolean bestValue = isBestValuePlan(plan);
        boolean mostPopular = !rows.isEmpty() && rows.get(0)[0] != null && planId.equals((Long) rows.get(0)[0]);

        return PlanRecommendationDto.builder()
                .planId(planId)
                .planName(plan.getName())
                .reason("")
                .activeUserCount((int) cnt)
                .adoptionRate(Math.round(adoption * 10.0) / 10.0)
                .mostPopular(mostPopular)
                .bestValue(bestValue)
                .build();
    }

    private String buildUserPayload(String role, Long userId, PlanRecommendationDto baseline) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("userRole", role);
        root.put("userId", userId);

        UserProfileDto profile = safeUser(userId);
        if (profile != null) {
            root.set("userProfile", objectMapper.valueToTree(profile));
        } else {
            root.putNull("userProfile");
        }

        if ("ENTERPRISE".equals(role)) {
            CompanyDto co = safeCompany(userId);
            if (co != null) {
                root.set("company", objectMapper.valueToTree(co));
            } else {
                root.putNull("company");
            }
        } else {
            root.putNull("company");
        }

        root.set("baselineRecommendation", objectMapper.valueToTree(baseline));

        ArrayNode plans = objectMapper.createArrayNode();
        for (PricingPlan p : pricingPlanService.findAllActive()) {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("id", p.getId());
            n.put("name", p.getName());
            n.put("description", p.getDescription() != null ? p.getDescription() : "");
            n.put("monthlyPrice", p.getMonthlyPrice() != null ? p.getMonthlyPrice() : 0);
            n.put("yearlyPrice", p.getYearlyPrice() != null ? p.getYearlyPrice() : 0);
            n.put("highlight", Boolean.TRUE.equals(p.getHighlight()));
            plans.add(n);
        }
        root.set("activePlans", plans);

        Optional<Subscription> subOpt = subscriptionRepository.findFirstByUserIdAndStatusIn(
                userId, List.of("ACTIVE", "EXPIRED"));
        if (subOpt.isPresent()) {
            Subscription s = subOpt.get();
            ObjectNode sn = objectMapper.createObjectNode();
            sn.put("status", s.getStatus());
            sn.put("billingCycle", s.getBillingCycle());
            if (s.getPricingPlan() != null) {
                sn.put("planId", s.getPricingPlan().getId());
                sn.put("planName", s.getPricingPlan().getName());
            }
            root.set("latestSubscription", sn);
        } else {
            root.putNull("latestSubscription");
        }

        return root.toString();
    }

    private UserProfileDto safeUser(Long userId) {
        try {
            return userClient.getUserById(userId);
        } catch (Exception e) {
            log.debug("Could not load user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    private CompanyDto safeCompany(Long userId) {
        try {
            return entrepriseClient.getCompanyByUserId(userId);
        } catch (Exception e) {
            log.debug("Could not load company for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    private static String normalizeUserRole(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            return "ENTERPRISE";
        }
        String r = userRole.trim();
        if ("STUDENT".equalsIgnoreCase(r)) {
            return "ETUDIANT";
        }
        return r.toUpperCase();
    }

    private static boolean isBestValuePlan(PricingPlan p) {
        if (p.getMonthlyPrice() == null || p.getYearlyPrice() == null) {
            return false;
        }
        double monthlyEq = p.getYearlyPrice() / 12.0;
        return monthlyEq < p.getMonthlyPrice() * 0.9;
    }

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return null;
        }
        String s = n.asText();
        if (s == null || s.isBlank()) {
            return null;
        }
        String u = s.trim().toUpperCase();
        if ("MONTHLY".equals(u) || "YEARLY".equals(u)) {
            return u;
        }
        return null;
    }
}
