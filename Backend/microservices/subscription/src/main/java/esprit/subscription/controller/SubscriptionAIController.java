package esprit.subscription.controller;

import esprit.subscription.DTO.ai.*;
import esprit.subscription.entity.InterventionLog;
import esprit.subscription.entity.PromoCode;
import esprit.subscription.service.PromoCodeService;
import esprit.subscription.service.SubscriptionAIService;
import esprit.subscription.service.TutorSalaryRecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/sub/ai", "/api/ai"})
public class SubscriptionAIController {

    private final SubscriptionAIService subscriptionAIService;
    private final PromoCodeService promoCodeService;
    private final TutorSalaryRecommendationService tutorSalaryRecommendationService;

    public SubscriptionAIController(SubscriptionAIService subscriptionAIService,
                                    PromoCodeService promoCodeService,
                                    TutorSalaryRecommendationService tutorSalaryRecommendationService) {
        this.subscriptionAIService = subscriptionAIService;
        this.promoCodeService = promoCodeService;
        this.tutorSalaryRecommendationService = tutorSalaryRecommendationService;
    }

    @GetMapping("/churn")
    public ResponseEntity<ChurnRiskDto> churn(@RequestParam Long userId) {
        return ResponseEntity.ok(subscriptionAIService.calculateChurnRisk(userId));
    }

    @GetMapping("/churn/all")
    public ResponseEntity<List<ChurnRiskDto>> churnAll() {
        return ResponseEntity.ok(subscriptionAIService.calculateAllChurnRisks());
    }

    @GetMapping("/promo-targets")
    public ResponseEntity<List<PromoTargetDto>> promoTargets() {
        return ResponseEntity.ok(subscriptionAIService.getPromoTargets());
    }

    @PostMapping("/promo/send")
    public ResponseEntity<?> sendPromo(@RequestParam Long userId, @RequestParam int discount) {
        try {
            PromoCode p = promoCodeService.createAiTargetingPromo(userId, discount);
            return ResponseEntity.ok(p);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/dna")
    public ResponseEntity<SubscriptionDnaDto> dna(@RequestParam Long userId) {
        return ResponseEntity.ok(subscriptionAIService.calculateDna(userId));
    }

    @GetMapping("/dna/all")
    public ResponseEntity<List<SubscriptionDnaDto>> dnaAll() {
        return ResponseEntity.ok(subscriptionAIService.calculateAllDna());
    }

    @GetMapping("/intervention")
    public ResponseEntity<InterventionDto> intervention(@RequestParam Long userId) {
        return ResponseEntity.ok(subscriptionAIService.getIntervention(userId));
    }

    @GetMapping("/intervention/all")
    public ResponseEntity<List<InterventionDto>> interventionAll() {
        return ResponseEntity.ok(subscriptionAIService.getAllInterventions());
    }

    @GetMapping("/contact-window")
    public ResponseEntity<ContactWindowDto> contactWindow(@RequestParam Long userId) {
        return ResponseEntity.ok(subscriptionAIService.getOptimalContactWindow(userId));
    }

    @GetMapping("/contact-window/all")
    public ResponseEntity<List<ContactWindowDto>> contactWindowAll() {
        return ResponseEntity.ok(subscriptionAIService.getAllContactWindows());
    }

    @GetMapping("/momentum")
    public ResponseEntity<MomentumDto> momentum() {
        return ResponseEntity.ok(subscriptionAIService.calculateMomentum());
    }

    @GetMapping("/cohort-survival")
    public ResponseEntity<List<CohortSurvivalDTO>> cohortSurvival() {
        return ResponseEntity.ok(subscriptionAIService.cohortSurvival());
    }

    @GetMapping("/monte-carlo")
    public ResponseEntity<MonteCarloForecastDTO> monteCarlo() {
        return ResponseEntity.ok(subscriptionAIService.monteCarloRevenue());
    }

    @GetMapping("/ltv")
    public ResponseEntity<List<LTVUserDTO>> ltv() {
        return ResponseEntity.ok(subscriptionAIService.ltvDecay());
    }

    @PostMapping("/intervention-log")
    public ResponseEntity<InterventionLog> interventionLog(@RequestBody InterventionLogRequestDTO body) {
        try {
            return ResponseEntity.ok(subscriptionAIService.recordInterventionLog(body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/intervention-accuracy")
    public ResponseEntity<List<ActionAccuracyDTO>> interventionAccuracy() {
        return ResponseEntity.ok(subscriptionAIService.interventionAccuracy());
    }

    @GetMapping("/upgrade-pressure")
    public ResponseEntity<List<UpgradeCandidateDTO>> upgradePressure() {
        return ResponseEntity.ok(subscriptionAIService.upgradePressure());
    }

    @GetMapping("/anomalies")
    public ResponseEntity<List<AnomalyDTO>> anomalies() {
        return ResponseEntity.ok(subscriptionAIService.behavioralAnomalies());
    }

    @GetMapping("/tutor-salary-recommendation")
    public ResponseEntity<?> tutorSalaryRecommendation(@RequestParam Long userId,
                                                       @RequestParam(required = false) Double targetMonthlyRevenue) {
        try {
            return ResponseEntity.ok(
                    tutorSalaryRecommendationService.recommend(userId, targetMonthlyRevenue));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }
}
