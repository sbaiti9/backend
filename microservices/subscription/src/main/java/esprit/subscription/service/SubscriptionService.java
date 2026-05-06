package esprit.subscription.service;

import esprit.subscription.DTO.PlanRecommendationDto;
import esprit.subscription.DTO.CompanyDto;
import esprit.subscription.DTO.UserProfileDto;
import esprit.subscription.client.EntrepriseClient;
import esprit.subscription.client.UserClient;
import esprit.subscription.entity.PaymentRecord;
import esprit.subscription.entity.PricingPlan;
import esprit.subscription.entity.Subscription;
import esprit.subscription.entity.UserLoyaltyAccount;
import esprit.subscription.repository.PaymentRecordRepository;
import esprit.subscription.repository.SubscriptionRepository;
import esprit.subscription.repository.UserLoyaltyAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final EntrepriseClient entrepriseClient;
    private final PricingPlanService pricingPlanService;
    private final PaymentRecordRepository paymentRecordRepository;
    private final EmailService emailService;
    private final PlanAdvisorAiService planAdvisorAiService;
    private final StripeSubscriptionSyncService stripeSubscriptionSyncService;
    private final LoyaltyService loyaltyService;
    private final UserLoyaltyAccountRepository loyaltyAccountRepository;
    private final UserClient userClient;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                                EntrepriseClient entrepriseClient,
                                PricingPlanService pricingPlanService,
                                PaymentRecordRepository paymentRecordRepository,
                                EmailService emailService,
                                PlanAdvisorAiService planAdvisorAiService,
                                StripeSubscriptionSyncService stripeSubscriptionSyncService,
                                LoyaltyService loyaltyService,
                                UserLoyaltyAccountRepository loyaltyAccountRepository,
                                UserClient userClient) {
        this.subscriptionRepository = subscriptionRepository;
        this.entrepriseClient = entrepriseClient;
        this.pricingPlanService = pricingPlanService;
        this.paymentRecordRepository = paymentRecordRepository;
        this.emailService = emailService;
        this.planAdvisorAiService = planAdvisorAiService;
        this.stripeSubscriptionSyncService = stripeSubscriptionSyncService;
        this.loyaltyService = loyaltyService;
        this.loyaltyAccountRepository = loyaltyAccountRepository;
        this.userClient = userClient;
    }

    @Cacheable(value = "subscriptions-all", key = "'all'")
    public List<Subscription> findAll() {
        return subscriptionRepository.findAll();
    }

    @Cacheable(value = "subscriptions-byUserId", key = "#userId")
    public List<Subscription> findByUserId(Long userId) {
        return subscriptionRepository.findByUserId(userId);
    }

    /**
     * Returns the current ACTIVE subscription for a user. Does NOT include EXPIRED.
     */
    @Cacheable(value = "subscriptions-activeByUserId", key = "#userId")
    public Optional<Subscription> findActiveSubscription(Long userId) {
        return subscriptionRepository.findFirstByUserIdAndStatusIn(userId, List.of("ACTIVE"));
    }

    /**
     * Latest subscription for status checks (ACTIVE, PAUSED, or EXPIRED).
     */
    @Cacheable(value = "subscriptions-latestByUserId", key = "#userId")
    public Optional<Subscription> findLatestSubscription(Long userId) {
        return subscriptionRepository.findTopByUserIdAndStatusInOrderByStartDateDesc(
                userId, List.of("ACTIVE", "PAUSED", "EXPIRED"));
    }

    /** Billing UI: show ACTIVE or PAUSED subscription card. */
    @Cacheable(value = "subscriptions-activeOrPausedByUserId", key = "#userId")
    public Optional<Subscription> findActiveOrPausedForUser(Long userId) {
        return subscriptionRepository.findTopByUserIdAndStatusInOrderByStartDateDesc(
                userId, List.of("ACTIVE", "PAUSED"));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "subscriptions", key = "#id")
    public Optional<Subscription> findById(Long id) {
        return subscriptionRepository.findById(id);
    }

    @Transactional
    @CacheEvict(value = {
            "subscriptions",
            "subscriptions-all",
            "subscriptions-byUserId",
            "subscriptions-activeByUserId",
            "subscriptions-latestByUserId",
            "subscriptions-activeOrPausedByUserId"
    }, allEntries = true)
    public Subscription attachStripeSubscriptionId(Long subscriptionId, String stripeSubscriptionId) {
        Subscription s = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + subscriptionId));
        if (stripeSubscriptionId != null && !stripeSubscriptionId.isBlank()) {
            s.setStripeSubscriptionId(stripeSubscriptionId.trim());
        }
        return subscriptionRepository.save(s);
    }

    @Transactional
    @CacheEvict(value = {
            "subscriptions",
            "subscriptions-all",
            "subscriptions-byUserId",
            "subscriptions-activeByUserId",
            "subscriptions-latestByUserId",
            "subscriptions-activeOrPausedByUserId"
    }, allEntries = true)
    public Subscription subscribe(Subscription subscription) {
        for (Subscription oldSub : subscriptionRepository.findByUserId(subscription.getUserId())) {
            if ("ACTIVE".equalsIgnoreCase(oldSub.getStatus()) || "PAUSED".equalsIgnoreCase(oldSub.getStatus())) {
                oldSub.setStatus("CANCELLED");
                oldSub.setEndDate(LocalDateTime.now());
                oldSub.setPauseEndAt(null);
                oldSub.setPausedAt(null);
                subscriptionRepository.save(oldSub);
                log.info("Previous subscription {} cancelled for userId={}", oldSub.getId(), subscription.getUserId());
            }
        }

        subscription.setStatus("ACTIVE");
        subscription.setStartDate(LocalDateTime.now());
        subscription.setCreatedAt(LocalDateTime.now());

        if ("YEARLY".equalsIgnoreCase(subscription.getBillingCycle())) {
            subscription.setEndDate(LocalDateTime.now().plusYears(1));
        } else {
            subscription.setEndDate(LocalDateTime.now().plusMonths(1));
        }

        Subscription saved = subscriptionRepository.save(subscription);
        log.info("New subscription {} created for userId={} plan={} cycle={}",
                saved.getId(), saved.getUserId(),
                saved.getPricingPlan() != null ? saved.getPricingPlan().getName() : "?",
                saved.getBillingCycle());
        return saved;
    }

    @Transactional
    public Subscription cancel(Long id) {
        Optional<Subscription> optionalSub = subscriptionRepository.findById(id);
        if (optionalSub.isPresent()) {
            Subscription subscription = optionalSub.get();
            subscription.setStatus("CANCELLED");
            subscription.setEndDate(LocalDateTime.now());
            log.info("Subscription {} cancelled", id);
            return subscriptionRepository.save(subscription);
        }
        log.warn("Cancel requested for unknown subscription id={}", id);
        return null;
    }

    @Transactional
    public Subscription toggleAutoRenew(Long id) {
        Subscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subscription not found: " + id));
        sub.setAutoRenew(!sub.isAutoRenew());
        log.info("AutoRenew toggled to {} for subscription {}", sub.isAutoRenew(), id);
        return subscriptionRepository.save(sub);
    }

    @Transactional
    public Subscription setAutoRenew(Long id, boolean enabled) {
        Subscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subscription not found: " + id));
        sub.setAutoRenew(enabled);
        log.info("AutoRenew set to {} for subscription {}", enabled, id);
        return subscriptionRepository.save(sub);
    }

    /**
     * Renews subscriptions that expire today, {@code autoRenew=true}, {@code ACTIVE}.
     */
    @Transactional
    public void processAutoRenewalsDueToday() {
        LocalDate day = LocalDate.now();
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();
        List<Subscription> due = subscriptionRepository.findActiveAutoRenewEndingBetween(start, end);
        for (Subscription old : due) {
            if (!old.isAutoRenew() || old.getPricingPlan() == null) {
                continue;
            }
            try {
                Subscription neu = new Subscription();
                neu.setUserId(old.getUserId());
                neu.setPricingPlan(old.getPricingPlan());
                neu.setBillingCycle(old.getBillingCycle());
                neu.setUserRole(old.getUserRole());
                neu.setAutoRenew(true);
                Subscription created = subscribe(neu);

                double amount = "YEARLY".equalsIgnoreCase(old.getBillingCycle())
                        ? Optional.ofNullable(old.getPricingPlan().getYearlyPrice()).orElse(0.0)
                        : Optional.ofNullable(old.getPricingPlan().getMonthlyPrice()).orElse(0.0);

                PaymentRecord pr = new PaymentRecord();
                pr.setUserId(old.getUserId());
                pr.setSubscriptionId(created.getId());
                pr.setType("RENEWAL");
                pr.setAmount(amount);
                pr.setStatus("SUCCESS");
                pr.setExternalPaymentRef("AUTO_RENEW_" + old.getId() + "_" + day);
                pr.setPlanName(old.getPricingPlan() != null ? old.getPricingPlan().getName() : null);
                pr.setBillingCycle(old.getBillingCycle());
                pr.setEndDate(created.getEndDate());
                paymentRecordRepository.save(pr);

                try {
                    loyaltyService.applySuccessfulRenewal(created.getUserId(), 0);
                } catch (Exception ex) {
                    log.warn("Loyalty update failed during auto-renew for user {}: {}", created.getUserId(), ex.getMessage());
                }

                var company = entrepriseClient.getCompanyByUserId(created.getUserId());
                if (company != null && company.getEmail() != null) {
                    emailService.sendAutoRenewalEmail(
                            company.getEmail(),
                            company.getName() != null ? company.getName() : "Client",
                            old.getPricingPlan().getName(),
                            created.getEndDate());
                }
                log.info("Auto-renew processed for userId={} newSubscriptionId={}", old.getUserId(), created.getId());
            } catch (Exception e) {
                log.error("Auto-renew failed for subscription id={}", old.getId(), e);
            }
        }
    }

    /**
     * Stats + catalog baseline, then Groq personalization when {@code userId} is set.
     */
    public PlanRecommendationDto recommendPlanWithAi(String userRole, Long userId) {
        PlanRecommendationDto baseline = recommendPlan(userRole);
        if (userId == null) {
            return baseline;
        }
        return planAdvisorAiService.advise(userRole, userId, baseline);
    }

    public PlanRecommendationDto recommendPlan(String userRole) {
        String role = normalizeUserRole(userRole);
        long totalForRole = subscriptionRepository.countActiveByUserRoleNormalized(role);
        List<Object[]> rows = subscriptionRepository.countActiveByPlanForRole(role);

        if (rows.isEmpty() || totalForRole == 0) {
            rows = subscriptionRepository.countActivePlansWithIds();
            totalForRole = subscriptionRepository.countByStatus("ACTIVE");
        }

        if (rows.isEmpty()) {
            return buildRecommendationFromCatalog(role);
        }

        Object[] top = rows.get(0);
        Long planId = (Long) top[0];
        String planName = (String) top[1];
        long cnt = (Long) top[2];

        Optional<PricingPlan> planOpt = pricingPlanService.findById(planId);
        PricingPlan plan = planOpt.orElse(null);

        double adoption = totalForRole > 0 ? (cnt * 100.0 / totalForRole) : 0.0;
        boolean bestValue = plan != null && isBestValuePlan(plan);
        boolean mostPopular = true;

        String reason = buildRecommendationReason(role, adoption, cnt, bestValue, plan);

        return PlanRecommendationDto.builder()
                .planId(planId)
                .planName(planName)
                .reason(reason)
                .activeUserCount((int) cnt)
                .adoptionRate(Math.round(adoption * 10.0) / 10.0)
                .mostPopular(mostPopular)
                .bestValue(bestValue)
                .build();
    }

    private PlanRecommendationDto buildRecommendationFromCatalog(String role) {
        List<PricingPlan> active = pricingPlanService.findAllActive();
        if (active.isEmpty()) {
            return PlanRecommendationDto.builder()
                    .planId(null)
                    .planName("—")
                    .reason("Aucun plan actif — configurez les tarifs dans l’administration.")
                    .activeUserCount(0)
                    .adoptionRate(0)
                    .mostPopular(false)
                    .bestValue(false)
                    .build();
        }
        PricingPlan p = active.stream().filter(pp -> Boolean.TRUE.equals(pp.getHighlight()))
                .findFirst()
                .orElse(active.get(0));
        boolean bestValue = isBestValuePlan(p);
        String label = roleLabelFr(role);
        return PlanRecommendationDto.builder()
                .planId(p.getId())
                .planName(p.getName())
                .reason("Pas encore assez de données — suggestion basée sur le catalogue (" + label + ")."
                        + (Boolean.TRUE.equals(p.getHighlight()) ? " · Recommandé par Skillio" : "")
                        + (bestValue ? " · Meilleur rapport qualité/prix" : ""))
                .activeUserCount(0)
                .adoptionRate(0)
                .mostPopular(false)
                .bestValue(bestValue)
                .build();
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

    private static String roleLabelFr(String normalizedRole) {
        return switch (normalizedRole) {
            case "ENTERPRISE" -> "entreprises";
            case "ETUDIANT" -> "étudiants";
            case "TUTEUR" -> "tuteurs";
            default -> "utilisateurs";
        };
    }

    private static boolean isBestValuePlan(PricingPlan p) {
        if (p.getMonthlyPrice() == null || p.getYearlyPrice() == null) {
            return false;
        }
        double monthlyEq = p.getYearlyPrice() / 12.0;
        return monthlyEq < p.getMonthlyPrice() * 0.9;
    }

    private String buildRecommendationReason(String role, double adoption, long activeCount,
                                            boolean bestValue, PricingPlan plan) {
        String label = roleLabelFr(role);
        StringBuilder sb = new StringBuilder();
        if (adoption > 50) {
            sb.append(String.format("Choix de %.0f%% des utilisateurs actifs %s", adoption, label));
        } else if (adoption >= 20) {
            sb.append(String.format("Populaire parmi les %s — %d abonnés actifs", label, activeCount));
        } else {
            sb.append(String.format("Plan fréquent pour les %s — %d abonnés actifs", label, activeCount));
        }
        if (bestValue) {
            sb.append(" · Meilleur rapport qualité/prix");
        }
        if (plan != null && Boolean.TRUE.equals(plan.getHighlight())) {
            sb.append(" · Recommandé par Skillio");
        }
        return sb.toString();
    }

    @Transactional
    @CacheEvict(value = {
            "subscriptions",
            "subscriptions-all",
            "subscriptions-byUserId",
            "subscriptions-activeByUserId",
            "subscriptions-latestByUserId",
            "subscriptions-activeOrPausedByUserId"
    }, allEntries = true)
    public void delete(Long id) {
        subscriptionRepository.deleteById(id);
    }

    public String checkSubscriptionStatus(Long userId) {
        Optional<Subscription> optionalSub = findLatestSubscription(userId);

        if (optionalSub.isEmpty()) {
            return "NO_SUBSCRIPTION";
        }

        Subscription subscription = optionalSub.get();
        LocalDateTime now = LocalDateTime.now();

        // If paused, we calculate remaining time and return PAUSED status immediately.
        if ("PAUSED".equalsIgnoreCase(subscription.getStatus())) {
            // Note: We return PAUSED status, but logic below can be used if we need 
            // to display "Paused since X days with Y days left".
            return "PAUSED";
        }

        if (subscription.getEndDate() == null) {
            return "ACTIVE";
        }

        long daysLeft = java.time.Duration.between(now, subscription.getEndDate()).toDays();

        if (daysLeft < 0) {
            subscription.setStatus("EXPIRED");
            subscriptionRepository.save(subscription);
            return "EXPIRED";
        }

        if (daysLeft <= 7) {
            return "EXPIRING_SOON";
        }

        return "ACTIVE";
    }

    /**
     * Pause like Netflix: access suspended until {@code pauseEndAt}.
     * The endDate is ONLY shifted when the user RESUMES, based on the actual time spent in pause.
     */
    @Transactional
    public Subscription pauseSubscription(Long subscriptionId, Long userId, int pauseDays) {
        if (pauseDays != 7 && pauseDays != 14 && pauseDays != 30) {
            throw new IllegalArgumentException("pauseDays must be 7, 14, or 30");
        }
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
        if (!sub.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Subscription does not belong to this user");
        }
        if (!"ACTIVE".equalsIgnoreCase(sub.getStatus())) {
            throw new IllegalStateException("Only ACTIVE subscriptions can be paused");
        }
        if (sub.getEndDate() == null) {
            throw new IllegalStateException("Subscription has no end date");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime pauseEndAt = now.plusDays(pauseDays);

        sub.setStatus("PAUSED");
        sub.setPausedAt(now);
        sub.setPauseEndAt(pauseEndAt);
        // We DO NOT shift the endDate here. We shift it in applyResume() based on actual time elapsed.

        Subscription saved = subscriptionRepository.save(sub);
        stripeSubscriptionSyncService.pauseOnStripeIfPresent(sub.getStripeSubscriptionId(), pauseEndAt);
        log.info("Subscription {} paused for max {} days until {} (consumption frozen)", subscriptionId, pauseDays, pauseEndAt);
        return saved;
    }

    @Transactional
    public Subscription resumeSubscriptionEarly(Long subscriptionId, Long userId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
        if (!sub.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Subscription does not belong to this user");
        }
        if (!"PAUSED".equalsIgnoreCase(sub.getStatus())) {
            throw new IllegalStateException("Subscription is not paused");
        }
        return applyResume(sub);
    }

    @Transactional
    public void processPausedSubscriptionsResumeDue() {
        LocalDateTime now = LocalDateTime.now();
        for (Subscription sub : subscriptionRepository.findPausedDueToResume(now)) {
            try {
                applyResume(sub);
                log.info("Auto-resumed subscription {} after pause", sub.getId());
            } catch (Exception e) {
                log.warn("Failed to auto-resume subscription {}: {}", sub.getId(), e.getMessage());
            }
        }
    }

    private Subscription applyResume(Subscription sub) {
        LocalDateTime now = LocalDateTime.now();
        
        // Calculate the actual time the subscription was frozen
        if (sub.getPausedAt() != null && sub.getEndDate() != null) {
            java.time.Duration actualPauseDuration = java.time.Duration.between(sub.getPausedAt(), now);
            // We shift the expiration date forward by the exact precision of time the user was paused
            sub.setEndDate(sub.getEndDate().plus(actualPauseDuration));
            log.info("Subscription {} resumed. EndDate shifted forward by duration: {}.", sub.getId(), actualPauseDuration);
        }

        sub.setStatus("ACTIVE");
        sub.setPauseEndAt(null);
        sub.setPausedAt(null);
        
        Subscription saved = subscriptionRepository.save(sub);
        stripeSubscriptionSyncService.resumeOnStripeIfPresent(sub.getStripeSubscriptionId());
        return saved;
    }

    // ── Stats ──────────────────────────────────────────────────────────────────

    public long countAll() {
        return subscriptionRepository.count();
    }

    public long countByStatus(String status) {
        return subscriptionRepository.countByStatus(status);
    }

    public List<Object[]> countByBillingCycle() {
        return subscriptionRepository.countByBillingCycle();
    }

    public List<Object[]> countByUserRole() {
        return subscriptionRepository.countByUserRole();
    }

    public List<Object[]> countGroupByStatus() {
        return subscriptionRepository.countGroupByStatus();
    }

    public List<Object[]> countByPlanName() {
        return subscriptionRepository.countByPlanName();
    }

    public List<Object[]> countActiveByPlanName() {
        return subscriptionRepository.countActiveByPlanName();
    }

    public Double calculateMonthlyRevenue() {
        Double result = subscriptionRepository.calculateMonthlyRevenue();
        return result != null ? result : 0.0;
    }

    public Double calculateYearlyRevenue() {
        Double result = subscriptionRepository.calculateYearlyRevenue();
        return result != null ? result : 0.0;
    }

    @Transactional(readOnly = true)
    public Optional<PaymentRecord> findPaymentByExternalRef(String externalRef) {
        return paymentRecordRepository.findFirstByExternalPaymentRefAndStatus(externalRef, "SUCCESS");
    }

    @Transactional
    public void recordSuccessfulCheckoutPayment(
            Long userId,
            Long subscriptionId,
            String externalSessionId,
            String planName,
            String billingCycle,
            LocalDateTime endDate
    ) {
        PaymentRecord pr = new PaymentRecord();
        pr.setUserId(userId);
        pr.setSubscriptionId(subscriptionId);
        pr.setType("CHECKOUT");
        pr.setStatus("SUCCESS");
        pr.setExternalPaymentRef(externalSessionId);
        pr.setPlanName(planName);
        pr.setBillingCycle(billingCycle);
        pr.setEndDate(endDate);
        paymentRecordRepository.save(pr);
    }

    @Transactional
    public void saveLoyaltyAccount(UserLoyaltyAccount account) {
        loyaltyAccountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public UserProfileDto getUserProfile(Long userId) {
        return userClient.getUserById(userId);
    }

    @Transactional(readOnly = true)
    public CompanyDto getCompanyProfile(Long userId) {
        return entrepriseClient.getCompanyByUserId(userId);
    }
}
