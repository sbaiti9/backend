package esprit.subscription.controller;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import esprit.subscription.DTO.PauseSubscriptionRequest;
import esprit.subscription.DTO.PlanRecommendationDto;
import esprit.subscription.DTO.PromoValidationDto;
import esprit.subscription.DTO.RevenueForecastDto;
import esprit.subscription.DTO.LoyaltyStatusDto;
import esprit.subscription.DTO.subb;
import esprit.subscription.entity.FraudAlert;
import esprit.subscription.entity.PricingPlan;
import esprit.subscription.entity.PromoCode;
import esprit.subscription.entity.Subscription;
import esprit.subscription.entity.UserLoyaltyAccount;
import esprit.subscription.service.FraudDetectionService;
import esprit.subscription.service.LoyaltyService;
import esprit.subscription.service.PricingPlanService;
import esprit.subscription.service.PromoCodeService;
import esprit.subscription.service.RevenueIntelligenceService;
import esprit.subscription.service.StripeLoyaltyCouponService;
import esprit.subscription.service.SubscriptionService;
import esprit.subscription.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import esprit.subscription.service.GeoEventNotifier;
import esprit.subscription.service.LivenessPaymentGuard;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/sub")
public class SubscriptionRESTApi {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionRESTApi.class);

    /** Stripe minimum charge for EUR (smallest unit — cents). */
    private static final long STRIPE_MIN_AMOUNT_CENTS_EUR = 50L;

    private final PricingPlanService pricingPlanService;
    private final SubscriptionService subscriptionService;
    private final PromoCodeService promoCodeService;
    private final FraudDetectionService fraudDetectionService;
    private final RevenueIntelligenceService revenueIntelligenceService;
    private final LivenessPaymentGuard livenessPaymentGuard;
    private final GeoEventNotifier geoEventNotifier;
    private final LoyaltyService loyaltyService;
    private final StripeLoyaltyCouponService stripeLoyaltyCouponService;
    private final EmailService emailService;

    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    public SubscriptionRESTApi(PricingPlanService pricingPlanService,
                                SubscriptionService subscriptionService,
                                PromoCodeService promoCodeService,
                                FraudDetectionService fraudDetectionService,
                                RevenueIntelligenceService revenueIntelligenceService,
                                LivenessPaymentGuard livenessPaymentGuard,
                                GeoEventNotifier geoEventNotifier,
                                LoyaltyService loyaltyService,
                                StripeLoyaltyCouponService stripeLoyaltyCouponService,
                                EmailService emailService) {
        this.pricingPlanService = pricingPlanService;
        this.subscriptionService = subscriptionService;
        this.promoCodeService = promoCodeService;
        this.fraudDetectionService = fraudDetectionService;
        this.revenueIntelligenceService = revenueIntelligenceService;
        this.livenessPaymentGuard = livenessPaymentGuard;
        this.geoEventNotifier = geoEventNotifier;
        this.loyaltyService = loyaltyService;
        this.stripeLoyaltyCouponService = stripeLoyaltyCouponService;
        this.emailService = emailService;
    }

    // ─── Pricing Plan Endpoints ───────────────────────────────────────────────

    @GetMapping("/plans")
    public ResponseEntity<List<PricingPlan>> getActivePlans() {
        return new ResponseEntity<>(pricingPlanService.findAllActive(), HttpStatus.OK);
    }

    @GetMapping("/plans/all")
    public ResponseEntity<List<PricingPlan>> getAllPlans() {
        return new ResponseEntity<>(pricingPlanService.findAll(), HttpStatus.OK);
    }

    @GetMapping("/plans/{id}")
    public ResponseEntity<PricingPlan> getPlanById(@PathVariable Long id) {
        Optional<PricingPlan> plan = pricingPlanService.findById(id);
        return plan.map(value -> new ResponseEntity<>(value, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/plans")
    public ResponseEntity<PricingPlan> createPlan(@RequestBody PricingPlan plan) {
        return new ResponseEntity<>(pricingPlanService.save(plan), HttpStatus.CREATED);
    }

    @PutMapping("/plans/{id}")
    public ResponseEntity<PricingPlan> updatePlan(@PathVariable Long id, @RequestBody PricingPlan plan) {
        PricingPlan updated = pricingPlanService.update(id, plan);
        return updated != null
                ? new ResponseEntity<>(updated, HttpStatus.OK)
                : new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @DeleteMapping("/plans/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable Long id) {
        pricingPlanService.delete(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // ─── Promo Code Endpoints ─────────────────────────────────────────────────

    @GetMapping("/promo/validate")
    public ResponseEntity<PromoValidationDto> validatePromo(
            @RequestParam String code,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userRole,
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) String billingCycle) {
        Double baseAmount = null;
        if (planId != null) {
            Optional<PricingPlan> pl = pricingPlanService.findById(planId);
            if (pl.isPresent()) {
                PricingPlan plan = pl.get();
                String cycle = billingCycle != null ? billingCycle.trim() : "MONTHLY";
                if ("YEARLY".equalsIgnoreCase(cycle)) {
                    baseAmount = plan.getYearlyPrice();
                } else {
                    baseAmount = plan.getMonthlyPrice();
                }
            }
        }
        PromoValidationDto dto = promoCodeService.validateDetailed(
                code, userId, userRole, planId, billingCycle, baseAmount);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/promo/active")
    public ResponseEntity<PromoCode> getActivePromoForUser(@RequestParam Long userId) {
        return promoCodeService.getActiveUnusedPromoForUser(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/recommendations/plan")
    public ResponseEntity<PlanRecommendationDto> getRecommendedPlan(
            @RequestParam(required = false) String userRole,
            @RequestParam(required = false) Long userId) {
        if (userId != null) {
            return ResponseEntity.ok(subscriptionService.recommendPlanWithAi(userRole, userId));
        }
        return ResponseEntity.ok(subscriptionService.recommendPlan(userRole));
    }

    @PostMapping("/promo")
    public ResponseEntity<?> createPromoCode(@RequestParam Long userId, @RequestBody PromoCode promoCode) {
        try {
            return ResponseEntity.ok(promoCodeService.createPromoCode(userId, promoCode));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Subscription Endpoints ───────────────────────────────────────────────

    @GetMapping("/subscriptions")
    public ResponseEntity<List<Subscription>> getAll() {
        return new ResponseEntity<>(subscriptionService.findAll(), HttpStatus.OK);
    }

    @GetMapping("/subscriptions/user/{userId}")
    public ResponseEntity<List<Subscription>> getByUserId(@PathVariable Long userId) {
        return new ResponseEntity<>(subscriptionService.findByUserId(userId), HttpStatus.OK);
    }

    @GetMapping("/subscriptions/user/{userId}/current")
    public ResponseEntity<Subscription> getCurrentSubscription(@PathVariable Long userId) {
        Optional<Subscription> subscription = subscriptionService.findActiveOrPausedForUser(userId);
        return subscription.map(value -> new ResponseEntity<>(value, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NO_CONTENT));
    }

    @GetMapping("/subscriptions/user/{userId}/status")
    public ResponseEntity<?> getSubscriptionStatus(@PathVariable Long userId) {
        String status = subscriptionService.checkSubscriptionStatus(userId);

        Optional<Subscription> optionalSub = subscriptionService.findLatestSubscription(userId);
        long daysLeft = 0;
        if (optionalSub.isPresent() && optionalSub.get().getEndDate() != null) {
            daysLeft = java.time.Duration.between(
                    java.time.LocalDateTime.now(),
                    optionalSub.get().getEndDate()
            ).toDays();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("daysLeft", daysLeft);
        optionalSub.ifPresent(s -> {
            if (s.getPauseEndAt() != null) {
                body.put("pauseEndAt", s.getPauseEndAt().toString());
            }
            if ("PAUSED".equalsIgnoreCase(s.getStatus()) && s.getPauseEndAt() != null) {
                long untilPauseEnd = java.time.Duration.between(
                        java.time.LocalDateTime.now(), s.getPauseEndAt()).toDays();
                body.put("pauseDaysRemaining", Math.max(0, untilPauseEnd));
            }
        });
        return ResponseEntity.ok(body);
    }

    @GetMapping("/loyalty/{userId}")
    public ResponseEntity<LoyaltyStatusDto> getLoyaltyStatus(@PathVariable Long userId) {
        return ResponseEntity.ok(loyaltyService.getStatus(userId));
    }

    @PostMapping("/loyalty/{userId}/redeem-preview")
    public ResponseEntity<?> redeemPreview(@PathVariable Long userId, @RequestParam(defaultValue = "true") boolean redeemPoints) {
        try {
            int percent = loyaltyService.previewRedeemPercent(userId, redeemPoints);
            return ResponseEntity.ok(Map.of(
                    "redeemApplied", percent > 0,
                    "discountPercent", percent,
                    "pointsCost", percent > 0 ? LoyaltyService.REDEEM_POINTS_COST : 0
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/subscriptions/{id}/pause")
    public ResponseEntity<?> pauseSubscription(
            @PathVariable Long id,
            @RequestParam Long userId,
            @RequestBody PauseSubscriptionRequest body) {
        try {
            Subscription updated = subscriptionService.pauseSubscription(id, userId, body.getPauseDays());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/subscriptions/{id}/resume")
    public ResponseEntity<?> resumeSubscription(@PathVariable Long id, @RequestParam Long userId) {
        try {
            Subscription updated = subscriptionService.resumeSubscriptionEarly(id, userId);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Stripe : Create checkout session ────────────────────────────────────

    @PostMapping("/subscriptions")
    public ResponseEntity<?> createSubscription(@RequestBody subb subs, HttpServletRequest request) {
        try {
            String liveTok = subs.getLivenessToken();
            if (liveTok == null || liveTok.isBlank()) {
                liveTok = request.getHeader("X-Liveness-Token");
            }
            ResponseEntity<Map<String, String>> livenessDenied =
                    livenessPaymentGuard.check(liveTok, subs.getUserId());
            if (livenessDenied != null) {
                return livenessDenied;
            }

            PricingPlan plan = pricingPlanService.findById(subs.getPlanId())
                    .orElseThrow(() -> new IllegalArgumentException("Pricing plan not found"));

            if (plan.getMonthlyPrice() == null || plan.getYearlyPrice() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Pricing plan has missing prices — set monthly and yearly amounts in admin."));
            }

            Stripe.apiKey = stripeSecretKey;
            if (stripeSecretKey == null || stripeSecretKey.isBlank() || stripeSecretKey.contains("YOUR_")) {
                log.error("Stripe secret key is missing or placeholder");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                        "error", "Stripe is not configured (stripe.secret.key). Set a valid key in application.properties."));
            }

            double amount;
            if ("MONTHLY".equalsIgnoreCase(subs.getBillingCycle())) {
                amount = plan.getMonthlyPrice();
            } else if ("YEARLY".equalsIgnoreCase(subs.getBillingCycle())) {
                amount = plan.getYearlyPrice();
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid billing cycle — use MONTHLY or YEARLY"));
            }

            String promoForMetadata = "";
            if (subs.getPromoCode() != null && !subs.getPromoCode().isBlank()) {
                double baseBeforePromo = amount;
                PromoValidationDto pv = promoCodeService.validateDetailed(
                        subs.getPromoCode().trim(),
                        subs.getUserId(),
                        subs.getUserRole(),
                        subs.getPlanId(),
                        subs.getBillingCycle(),
                        baseBeforePromo);
                if (!pv.isValid()) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", pv.getMessage() != null ? pv.getMessage() : "Code promo invalide"));
                }
                Optional<PromoCode> promoOpt = promoCodeService.findNormalized(subs.getPromoCode().trim());
                if (promoOpt.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Code promo introuvable"));
                }
                PromoCode promoCode = promoOpt.get();
                amount = promoCodeService.applyDiscountToAmount(baseBeforePromo, promoCode);
                promoForMetadata = promoCode.getCode();
            }

            int pointsToConsume = 0;
            log.info("[SUBSCRIPTION] Processing payment for userId: {}. RedeemPoints requested: {}", subs.getUserId(), subs.getRedeemPoints());
            
            if (Boolean.TRUE.equals(subs.getRedeemPoints())) {
                try {
                    int percent = loyaltyService.previewRedeemPercent(subs.getUserId(), true);
                    double discount = Math.round((amount * percent / 100.0) * 100.0) / 100.0;
                    amount = amount - discount;
                    pointsToConsume = LoyaltyService.REDEEM_POINTS_COST;
                    log.info("[LOYALTY] Discount of {}% applied ({} EUR). New total: {}", percent, discount, amount);
                } catch (Exception e) {
                    log.error("[LOYALTY] Reduction failed for user {}: {}", subs.getUserId(), e.getMessage());
                    return ResponseEntity.badRequest().body(Map.of("error", "Points de fidélité insuffisants ou invalides."));
                }
            }

            long amountInCents = Math.round(amount * 100);
            log.info("[STRIPE] Final session amount: {} cents", amountInCents);
            if (amountInCents < STRIPE_MIN_AMOUNT_CENTS_EUR) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", String.format(
                                "Amount %.2f EUR (%d cents) is below Stripe minimum for EUR (%d cents). Raise plan price or reduce discount.",
                                amount, amountInCents, STRIPE_MIN_AMOUNT_CENTS_EUR)));
            }

            String planLabel = plan.getName() != null && !plan.getName().isBlank() ? plan.getName() : "Subscription";

            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl("http://localhost:4200/enterprise/subscription/success?session_id={CHECKOUT_SESSION_ID}"
                            + "&userId=" + subs.getUserId()
                            + "&planId=" + subs.getPlanId()
                            + "&billingCycle=" + subs.getBillingCycle()
                            + "&userRole=" + (subs.getUserRole() != null ? subs.getUserRole() : ""))
                    .setCancelUrl("http://localhost:4200/enterprise/subscription")
                    .putMetadata("userId", String.valueOf(subs.getUserId()))
                    .putMetadata("planId", String.valueOf(subs.getPlanId()))
                    .putMetadata("billingCycle", subs.getBillingCycle() != null ? subs.getBillingCycle() : "")
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency("eur")
                                                    .setUnitAmount(amountInCents)
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName(planLabel)
                                                                    .build())
                                                    .build())
                                    .build());

            if (!promoForMetadata.isBlank()) {
                paramsBuilder.putMetadata("promoCode", promoForMetadata);
            }
            if (pointsToConsume > 0) {
                paramsBuilder.putMetadata("pointsToConsume", String.valueOf(pointsToConsume));
            }

            Session session = Session.create(paramsBuilder.build());

            return ResponseEntity.ok(Map.of(
                    "sessionId", session.getId(),
                    "url", session.getUrl()
            ));
        } catch (StripeException e) {
            log.warn("Stripe checkout session failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "Stripe error: " + e.getMessage(),
                    "stripeCode", e.getCode() != null ? e.getCode() : ""));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("createSubscription failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", e.getMessage() != null ? e.getMessage() : "Unexpected error"));
        }
    }

    // ─── Stripe : Confirm payment and persist subscription ───────────────────

    @GetMapping("/payment/confirm")
    public ResponseEntity<?> confirmSubscription(
            HttpServletRequest httpRequest,
            @RequestParam String session_id,
            @RequestParam Long userId,
            @RequestParam Long planId,
            @RequestParam String billingCycle,
            @RequestParam(required = false) String userRole) {

        try {
            if (stripeSecretKey == null || stripeSecretKey.isBlank() || stripeSecretKey.contains("YOUR_")) {
                log.error("confirmSubscription: Stripe secret key missing or placeholder");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                        "error", "Stripe is not configured (stripe.secret.key)."));
            }

            String cycle = billingCycle != null ? billingCycle.trim() : "";
            if (cycle.isEmpty()
                    || (!"MONTHLY".equalsIgnoreCase(cycle) && !"YEARLY".equalsIgnoreCase(cycle))) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "billingCycle must be MONTHLY or YEARLY"));
            }
            cycle = cycle.toUpperCase();

            Stripe.apiKey = stripeSecretKey;
            Session session = Session.retrieve(session_id);

            String payStatus = session.getPaymentStatus();
            String sessionStatus = session.getStatus();
            log.info("Stripe session {} status={} payment_status={}", session_id, sessionStatus, payStatus);

            boolean completeOk = sessionStatus != null && "complete".equalsIgnoreCase(sessionStatus.trim());
            boolean paidOk = payStatus != null && (
                    "paid".equalsIgnoreCase(payStatus.trim())
                            || "no_payment_required".equalsIgnoreCase(payStatus.trim()));

            if (!completeOk || !paidOk) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Payment not completed",
                        "paymentStatus", payStatus != null ? payStatus : "",
                        "sessionStatus", sessionStatus != null ? sessionStatus : ""));
            }

            Optional<esprit.subscription.entity.PaymentRecord> existing =
                    subscriptionService.findPaymentByExternalRef(session_id);
            if (existing.isPresent() && existing.get().getSubscriptionId() != null) {
                Long sid = existing.get().getSubscriptionId();
                Subscription already = subscriptionService.findById(sid).orElse(null);
                if (already != null) {
                    return ResponseEntity.ok(subscriptionToJsonMap(already));
                }
            }

            PricingPlan plan = pricingPlanService.findById(planId)
                    .orElseThrow(() -> new IllegalArgumentException("Pricing plan not found — id=" + planId));

            Subscription subscription = new Subscription();
            subscription.setUserId(userId);
            subscription.setPricingPlan(plan);
            subscription.setBillingCycle(cycle);
            if (userRole != null && !userRole.isBlank()) {
                subscription.setUserRole(userRole.trim());
            }

            Subscription created = subscriptionService.subscribe(subscription);
            Long createdId = created.getId();

            String stripeSub = session.getSubscription();
            if (stripeSub != null && !stripeSub.isBlank()) {
                try {
                    created = subscriptionService.attachStripeSubscriptionId(createdId, stripeSub);
                } catch (Exception ex) {
                    log.warn("Could not attach Stripe subscription id to local subscription {}: {}", createdId, ex.getMessage());
                }
            }

            try {
                fraudDetectionService.analyseSubscription(created);
            } catch (Exception ex) {
                log.warn("Fraud analysis skipped (subscription still saved): {}", ex.getMessage());
            }

            Map<String, String> meta = session.getMetadata();
            if (meta != null) {
                String code = meta.get("promoCode");
                if (code != null && !code.isBlank()) {
                    try {
                        promoCodeService.validate(code).ifPresent(
                                p -> promoCodeService.recordUsage(p, userId, createdId));
                    } catch (Exception ex) {
                        log.warn("Promo usage not recorded: {}", ex.getMessage());
                    }
                }
            }

            int pointsToConsume = 0;
            if (meta != null && meta.get("pointsToConsume") != null) {
                try {
                    pointsToConsume = Integer.parseInt(meta.get("pointsToConsume"));
                } catch (NumberFormatException ignored) {
                    pointsToConsume = 0;
                }
            }

            LoyaltyService.LoyaltyRewardResult loyaltyResult =
                    loyaltyService.applySuccessfulRenewal(userId, pointsToConsume);

            if (loyaltyResult.milestoneReached() && loyaltyResult.milestoneDiscountPercent() > 0) {
                try {
                    var coupon = stripeLoyaltyCouponService.createMilestoneCoupon(
                            userId, loyaltyResult.milestoneMonth(), loyaltyResult.milestoneDiscountPercent());
                    UserLoyaltyAccount account = loyaltyResult.account();
                    account.setLastMilestonePromoCode(coupon.code());
                    account.setLastMilestoneStripeCouponId(coupon.couponId());
                    account.setLastMilestoneStripePromotionCodeId(coupon.promotionCodeId());
                    subscriptionService.saveLoyaltyAccount(account);

                    String email = null;
                    String name = "Utilisateur";
                    try {
                        var userProfile = subscriptionService.getUserProfile(userId);
                        if (userProfile != null) {
                            email = userProfile.getEmail();
                            if (userProfile.getName() != null && !userProfile.getName().isBlank()) {
                                name = userProfile.getName();
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    if (email == null || email.isBlank()) {
                        try {
                            var company = subscriptionService.getCompanyProfile(userId);
                            if (company != null) {
                                email = company.getEmail();
                                if (company.getName() != null && !company.getName().isBlank()) {
                                    name = company.getName();
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    if (email != null && !email.isBlank()) {
                        emailService.sendLoyaltyMilestoneEmail(
                                email,
                                name,
                                loyaltyResult.account().getLoyaltyTier().name(),
                                loyaltyResult.milestoneMonth(),
                                loyaltyResult.milestoneDiscountPercent(),
                                coupon.code());
                    }
                } catch (Exception ex) {
                    log.warn("Loyalty milestone reward creation failed: {}", ex.getMessage());
                }
            }

            subscriptionService.recordSuccessfulCheckoutPayment(
                    userId,
                    createdId,
                    session_id,
                    created.getPricingPlan() != null ? created.getPricingPlan().getName() : null,
                    created.getBillingCycle(),
                    created.getEndDate());

            Subscription forJson = subscriptionService.findById(createdId).orElse(created);
            try {
                geoEventNotifier.notifyPaymentAsync(userId, httpRequest);
            } catch (Exception ex) {
                log.debug("Geo event after payment skipped: {}", ex.getMessage());
            }
            return ResponseEntity.ok(subscriptionToJsonMap(forJson));
        } catch (StripeException e) {
            log.warn("Stripe session retrieve failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "Stripe error: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            Throwable cause = e.getMostSpecificCause();
            String detail = cause != null ? cause.getMessage() : e.getMessage();
            log.error("confirmSubscription DB constraint violation: {}", detail, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Subscription could not be saved — check subscriptions table (AUTO_INCREMENT, foreign keys).",
                    "detail", detail != null ? detail : "unknown"));
        } catch (Exception e) {
            log.error("confirmSubscription failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", e.getMessage() != null ? e.getMessage() : "Unexpected error"));
        }
    }

    // ─── Cancel / Delete subscription ────────────────────────────────────────

    @PutMapping("/subscriptions/{id}/cancel")
    public ResponseEntity<Subscription> cancelSubscription(@PathVariable Long id) {
        Subscription cancelled = subscriptionService.cancel(id);
        return cancelled != null
                ? new ResponseEntity<>(cancelled, HttpStatus.OK)
                : new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @PatchMapping("/subscriptions/{id}/auto-renew")
    public ResponseEntity<Subscription> patchAutoRenew(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean enabled) {
        try {
            if (enabled != null) {
                return ResponseEntity.ok(subscriptionService.setAutoRenew(id, enabled));
            }
            return ResponseEntity.ok(subscriptionService.toggleAutoRenew(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/subscriptions/{id}")
    public ResponseEntity<Void> deleteSubscription(@PathVariable Long id) {
        subscriptionService.delete(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // ─── Fraud Alert Endpoints ────────────────────────────────────────────────

    @GetMapping("/fraud/alerts")
    public ResponseEntity<List<FraudAlert>> getFraudAlerts(
            @RequestParam(required = false) String riskLevel) {
        List<FraudAlert> alerts = riskLevel != null && !riskLevel.isBlank()
                ? fraudDetectionService.findByRiskLevel(riskLevel)
                : fraudDetectionService.findAll();
        return ResponseEntity.ok(alerts);
    }

    @PatchMapping("/fraud/alerts/{id}/resolve")
    public ResponseEntity<FraudAlert> resolveFraudAlert(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(fraudDetectionService.resolve(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ─── Revenue & intelligence (admin prévisions) ───────────────────────────

    @GetMapping("/revenue/forecast")
    public ResponseEntity<RevenueForecastDto> getRevenueForecast() {
        return ResponseEntity.ok(revenueIntelligenceService.buildForecast());
    }

    /**
     * Déclenche un rafraîchissement des agrégats intelligence côté serveur
     * (les prévisions sont recalculées à partir des abonnements à chaque lecture).
     */
    @GetMapping("/intelligence/refresh")
    public ResponseEntity<Map<String, Object>> refreshIntelligence() {
        Instant refreshedAt = revenueIntelligenceService.refreshAggregates();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("refreshedAt", refreshedAt.toString());
        return ResponseEntity.ok(body);
    }

    // ─── Stats Endpoints ──────────────────────────────────────────────────────

    @GetMapping("/subscriptions/stats")
    public ResponseEntity<Map<String, Object>> getSubscriptionStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            stats.put("totalSubscriptions",     subscriptionService.countAll());
            stats.put("activeSubscriptions",    subscriptionService.countByStatus("ACTIVE"));
            stats.put("cancelledSubscriptions", subscriptionService.countByStatus("CANCELLED"));
            stats.put("expiredSubscriptions",   subscriptionService.countByStatus("EXPIRED"));

            List<Object[]> byCycle = subscriptionService.countByBillingCycle();
            Map<String, Long> cycleMap = new HashMap<>();
            for (Object[] row : byCycle) {
                if (row[0] != null) cycleMap.put((String) row[0], (Long) row[1]);
            }
            stats.put("byBillingCycle", cycleMap);

            List<Object[]> byRole = subscriptionService.countByUserRole();
            Map<String, Long> roleMap = new HashMap<>();
            for (Object[] row : byRole) {
                if (row[0] != null) roleMap.put((String) row[0], (Long) row[1]);
            }
            stats.put("byUserRole", roleMap);

            List<Object[]> byStatus = subscriptionService.countGroupByStatus();
            Map<String, Long> statusMap = new HashMap<>();
            for (Object[] row : byStatus) {
                if (row[0] != null) statusMap.put((String) row[0], (Long) row[1]);
            }
            stats.put("byStatus", statusMap);

            double monthly = subscriptionService.calculateMonthlyRevenue();
            double yearly  = subscriptionService.calculateYearlyRevenue();
            stats.put("monthlyRevenue", Math.round(monthly * 100.0) / 100.0);
            stats.put("yearlyRevenue",  Math.round(yearly  * 100.0) / 100.0);
            stats.put("totalRevenue",   Math.round((monthly + yearly) * 100.0) / 100.0);

            List<Object[]> subsByPlan = subscriptionService.countByPlanName();
            Map<String, Long> subsByPlanMap = new HashMap<>();
            for (Object[] row : subsByPlan) {
                if (row[0] != null) subsByPlanMap.put((String) row[0], (Long) row[1]);
            }
            stats.put("subscribersPerPlan", subsByPlanMap);

            List<Object[]> activeByPlan = subscriptionService.countActiveByPlanName();
            Map<String, Long> activePlanMap = new HashMap<>();
            for (Object[] row : activeByPlan) {
                if (row[0] != null) activePlanMap.put((String) row[0], (Long) row[1]);
            }
            stats.put("subscribersPerPlanActive", activePlanMap);

        } catch (Exception e) {
            stats.put("error", "Partial stats — " + e.getMessage());
        }

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/plans/stats")
    public ResponseEntity<Map<String, Object>> getPlanStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            stats.put("totalPlans",       pricingPlanService.countAll());
            stats.put("activePlans",      pricingPlanService.countByIsActive(true));
            stats.put("inactivePlans",    pricingPlanService.countByIsActive(false));
            stats.put("highlightedPlans", pricingPlanService.countByHighlight(true));

            List<Object[]> byName = pricingPlanService.countByName();
            Map<String, Long> nameMap = new HashMap<>();
            for (Object[] row : byName) {
                if (row[0] != null) nameMap.put((String) row[0], (Long) row[1]);
            }
            stats.put("byName", nameMap);

            Double avgMonthly = pricingPlanService.findAverageMonthlyPrice();
            Double minMonthly = pricingPlanService.findMinMonthlyPrice();
            Double maxMonthly = pricingPlanService.findMaxMonthlyPrice();
            Double avgYearly  = pricingPlanService.findAverageYearlyPrice();

            stats.put("averageMonthlyPrice",       avgMonthly != null ? Math.round(avgMonthly * 100.0) / 100.0 : 0);
            stats.put("cheapestMonthlyPrice",      minMonthly != null ? minMonthly : 0);
            stats.put("mostExpensiveMonthlyPrice", maxMonthly != null ? maxMonthly : 0);
            stats.put("averageYearlyPrice",        avgYearly  != null ? Math.round(avgYearly  * 100.0) / 100.0 : 0);

            List<Object[]> subsByPlan = subscriptionService.countByPlanName();
            Map<String, Long> subsByPlanMap = new HashMap<>();
            for (Object[] row : subsByPlan) {
                if (row[0] != null) subsByPlanMap.put((String) row[0], (Long) row[1]);
            }
            stats.put("subscribersPerPlan", subsByPlanMap);

        } catch (Exception e) {
            stats.put("error", "Partial stats — " + e.getMessage());
        }

        return ResponseEntity.ok(stats);
    }

    /** Safe JSON for clients — avoids Hibernate/Jackson issues on JPA entities. */
    private Map<String, Object> subscriptionToJsonMap(Subscription s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("userId", s.getUserId());
        m.put("userRole", s.getUserRole());
        if (s.getPricingPlan() != null) {
            m.put("planId", s.getPricingPlan().getId());
            m.put("planName", s.getPricingPlan().getName());
        } else {
            m.put("planId", null);
            m.put("planName", null);
        }
        m.put("billingCycle", s.getBillingCycle());
        m.put("status", s.getStatus());
        m.put("startDate", s.getStartDate());
        m.put("endDate", s.getEndDate());
        m.put("createdAt", s.getCreatedAt());
        m.put("autoRenew", s.isAutoRenew());
        try {
            LoyaltyStatusDto loyalty = loyaltyService.getStatus(s.getUserId());
            m.put("loyaltyMonths", loyalty.getLoyaltyMonths());
            m.put("walletPoints", loyalty.getWalletPoints());
            m.put("loyaltyTier", loyalty.getLoyaltyTier() != null ? loyalty.getLoyaltyTier().name() : "NONE");
        } catch (Exception ignored) {
        }
        return m;
    }
}
