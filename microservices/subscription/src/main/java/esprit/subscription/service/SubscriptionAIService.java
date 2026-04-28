package esprit.subscription.service;

import esprit.subscription.DTO.ai.*;
import esprit.subscription.entity.FraudAlert;
import esprit.subscription.entity.InterventionLog;
import esprit.subscription.entity.PaymentRecord;
import esprit.subscription.entity.PricingPlan;
import esprit.subscription.entity.PromoCode;
import esprit.subscription.entity.Subscription;
import esprit.subscription.repository.FraudAlertRepository;
import esprit.subscription.repository.InterventionLogRepository;
import esprit.subscription.repository.PaymentRecordRepository;
import esprit.subscription.repository.PricingPlanRepository;
import esprit.subscription.repository.PromoCodeRepository;
import esprit.subscription.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Module intelligence abonnements — logique pure Java, sans API externes.
 */
@Service
public class SubscriptionAIService {

    private static final String HIGH = "HIGH";
    private static final String MEDIUM = "MEDIUM";
    private static final String LOW = "LOW";

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final PricingPlanRepository pricingPlanRepository;
    private final InterventionLogRepository interventionLogRepository;

    public SubscriptionAIService(SubscriptionRepository subscriptionRepository,
                                 PaymentRecordRepository paymentRecordRepository,
                                 PromoCodeRepository promoCodeRepository,
                                 FraudAlertRepository fraudAlertRepository,
                                 PricingPlanRepository pricingPlanRepository,
                                 InterventionLogRepository interventionLogRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.fraudAlertRepository = fraudAlertRepository;
        this.pricingPlanRepository = pricingPlanRepository;
        this.interventionLogRepository = interventionLogRepository;
    }

    // ── Churn ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ChurnRiskDto calculateChurnRisk(Long userId) {
        List<Subscription> subs = subscriptionRepository.findByUserIdOrderByCreatedAtAsc(userId);
        if (subs.isEmpty()) {
            return ChurnRiskDto.builder()
                    .userId(userId)
                    .score(0)
                    .riskLevel(LOW)
                    .topReasons(List.of())
                    .recommendation("Aucune action requise")
                    .build();
        }

        List<ScoredReason> reasons = new ArrayList<>();
        int score = 0;

        Optional<Subscription> activeOpt = subs.stream().filter(s -> "ACTIVE".equalsIgnoreCase(s.getStatus())).findFirst();
        Subscription active = activeOpt.orElse(null);

        if (active != null) {
            if (!active.isAutoRenew()) {
                score += 25;
                reasons.add(new ScoredReason(25, "Renouvellement automatique désactivé"));
            }
            if ("MONTHLY".equalsIgnoreCase(active.getBillingCycle())) {
                score += 15;
                reasons.add(new ScoredReason(15, "Abonnement mensuel sans engagement"));
            }
            if (active.getEndDate() != null) {
                long days = ChronoUnit.DAYS.between(LocalDate.now(), active.getEndDate().toLocalDate());
                if (days <= 7) {
                    score += 20;
                    reasons.add(new ScoredReason(20, "Expiration dans moins de 7 jours"));
                } else if (days <= 30) {
                    score += 10;
                    reasons.add(new ScoredReason(10, "Expiration dans 8 à 30 jours"));
                }
            }
            if (active.getGracePeriodEnd() != null
                    && active.getGracePeriodEnd().isAfter(LocalDateTime.now())) {
                score += 20;
                reasons.add(new ScoredReason(20, "Période de grâce active"));
            }
        }

        long failedPayments = paymentRecordRepository.countByUserIdAndStatus(userId, "FAILED");
        if (failedPayments >= 2) {
            score += 20;
            reasons.add(new ScoredReason(20, "Paiements échoués répétés"));
        } else if (failedPayments == 1) {
            score += 10;
            reasons.add(new ScoredReason(10, "Paiement échoué détecté"));
        }

        boolean expiredNoRenewal = subs.stream().anyMatch(s -> "EXPIRED".equalsIgnoreCase(s.getStatus()))
                && active == null;
        if (expiredNoRenewal) {
            score += 15;
            reasons.add(new ScoredReason(15, "Abonnement expiré sans renouvellement récent"));
        }

        boolean fraudHigh = fraudAlertRepository.findByUserId(userId).stream()
                .anyMatch(a -> HIGH.equalsIgnoreCase(a.getRiskLevel()));
        if (fraudHigh) {
            score += 10;
            reasons.add(new ScoredReason(10, "Alerte fraude niveau élevé"));
        }

        score = Math.min(100, score);
        String level = score >= 70 ? HIGH : (score >= 40 ? MEDIUM : LOW);
        String reco = score >= 70
                ? "Envoyer une offre de rétention immédiatement"
                : (score >= 40 ? "Planifier un email de rappel dans 5 jours" : "Aucune action requise");

        List<String> top = reasons.stream()
                .sorted(Comparator.comparingInt((ScoredReason r) -> r.points).reversed())
                .limit(2)
                .map(r -> r.label)
                .collect(Collectors.toList());

        return ChurnRiskDto.builder()
                .userId(userId)
                .score(score)
                .riskLevel(level)
                .topReasons(top)
                .recommendation(reco)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ChurnRiskDto> calculateAllChurnRisks() {
        List<Long> ids = subscriptionRepository.findDistinctUserIds();
        if (ids == null) return new ArrayList<>();
        return ids.stream()
                .map(this::calculateChurnRisk)
                .sorted(Comparator.comparingInt(ChurnRiskDto::getScore).reversed())
                .collect(Collectors.toList());
    }

    // ── Promo targets ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PromoTargetDto> getPromoTargets() {
        List<PromoTargetDto> out = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Subscription s : subscriptionRepository.findAllActiveWithPlans()) {
            if (!"ACTIVE".equalsIgnoreCase(s.getStatus())) continue;
            if (s.isAutoRenew()) continue;
            if (s.getEndDate() == null) continue;
            long days = ChronoUnit.DAYS.between(LocalDate.now(), s.getEndDate().toLocalDate());
            if (days > 30 || days < 0) continue;

            Long uid = s.getUserId();
            ChurnRiskDto churn = calculateChurnRisk(uid);
            if (churn.getScore() < 40) continue;

            List<PromoCode> eligible = promoCodeRepository.findEligibleActiveForUser(uid, now);
            if (!eligible.isEmpty()) continue;

            String planName = s.getPricingPlan() != null ? s.getPricingPlan().getName() : "BASIC";
            int disc = recommendedDiscountPct(planName);
            int priority = churn.getScore() + (int) (30 - Math.min(30, Math.max(0, days)));

            String reason = "Expiration proche, risque de churn élevé, sans code promo actif et sans renouvellement auto.";
            out.add(PromoTargetDto.builder()
                    .userId(uid)
                    .planName(planName)
                    .daysUntilExpiry((int) days)
                    .churnScore(churn.getScore())
                    .recommendedDiscount(disc)
                    .priorityScore(priority)
                    .reason(reason)
                    .build());
        }
        out.sort(Comparator.comparingInt(PromoTargetDto::getPriorityScore).reversed());
        return out;
    }

    private static int recommendedDiscountPct(String planName) {
        String u = planName != null ? planName.toUpperCase() : "";
        if (u.contains("PREMIUM")) return 20;
        if (u.contains("PRO")) return 15;
        return 10;
    }

    // ── DNA ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SubscriptionDnaDto calculateDna(Long userId) {
        List<Subscription> subs = subscriptionRepository.findByUserIdOrderByCreatedAtAsc(userId);
        ChurnRiskDto churn = calculateChurnRisk(userId);
        int churnScore = churn.getScore();

        if (subs.isEmpty()) {
            return dnaDto(userId, "STANDARD", "Client standard", "gray",
                    "Maintenir l'engagement avec newsletters",
                    List.of("Aucun historique d'abonnement."));
        }

        boolean volatileProf = isVolatileProfile(subs);
        boolean bargain = isBargainHunter(userId, subs);
        boolean growing = isGrowing(subs);
        boolean loyal = isLoyalist(userId, subs);

        if (volatileProf) {
            return dnaDto(userId, "VOLATILE", "Profil volatile", "red",
                    "Envoyer un email de stabilisation + offre d'engagement",
                    List.of("Plusieurs annulations ou réinscriptions rapides détectées."));
        }
        if (churnScore >= 40) {
            return dnaDto(userId, "AT_RISK", "À risque", "orange",
                    "Intervention prioritaire — voir Smart Intervention Engine",
                    List.of("Score de churn élevé (" + churnScore + "/100)."));
        }
        if (bargain) {
            return dnaDto(userId, "BARGAIN_HUNTER", "Chasseur de promos", "amber",
                    "Offrir YEARLY avec remise 15% avant expiration",
                    List.of("Plusieurs codes promo ou cycles mensuels dominants."));
        }
        if (growing) {
            return dnaDto(userId, "GROWING", "En progression", "blue",
                    "Proposer plan supérieur avec comparaison des features",
                    List.of("Progression de plan ou renouvellements actifs constatés."));
        }
        if (loyal) {
            return dnaDto(userId, "LOYALIST", "Client fidèle", "green",
                    "Proposer un upgrade ou un plan ambassadeur",
                    List.of("Renouvellement automatique, cycle annuel et paiements sains."));
        }
        return dnaDto(userId, "STANDARD", "Client standard", "gray",
                "Maintenir l'engagement avec newsletters",
                List.of("Profil sans signal fort particulier."));
    }

    private SubscriptionDnaDto dnaDto(Long userId, String arch, String label, String color, String strategy, List<String> signals) {
        return SubscriptionDnaDto.builder()
                .userId(userId)
                .archetype(arch)
                .archetypeLabel(label)
                .color(color)
                .strategy(strategy)
                .signals(signals)
                .build();
    }

    private boolean isVolatileProfile(List<Subscription> subs) {
        long cancelled = subs.stream().filter(s -> "CANCELLED".equalsIgnoreCase(s.getStatus())).count();
        if (subs.size() >= 3 && cancelled >= 2) return true;
        List<Subscription> sorted = new ArrayList<>(subs);
        sorted.sort(Comparator.comparing(Subscription::getCreatedAt));
        for (int i = 1; i < sorted.size(); i++) {
            Subscription prev = sorted.get(i - 1);
            Subscription cur = sorted.get(i);
            if ("CANCELLED".equalsIgnoreCase(prev.getStatus()) && prev.getEndDate() != null) {
                long days = ChronoUnit.DAYS.between(prev.getEndDate(), cur.getCreatedAt());
                if (days >= 0 && days <= 30) return true;
            }
        }
        return false;
    }

    private boolean isBargainHunter(Long userId, List<Subscription> subs) {
        long promoCount = promoCodeRepository.countByCreatedByUserId(userId);
        boolean allMonthly = subs.stream().allMatch(s -> "MONTHLY".equalsIgnoreCase(s.getBillingCycle()));
        return promoCount >= 2 || (allMonthly && subs.size() >= 1 && promoCount >= 1);
    }

    private boolean isGrowing(List<Subscription> subs) {
        List<Subscription> sorted = subs.stream()
                .sorted(Comparator.comparing(Subscription::getCreatedAt))
                .collect(Collectors.toList());
        for (int i = 1; i < sorted.size(); i++) {
            int t0 = planTier(sorted.get(i - 1).getPricingPlan());
            int t1 = planTier(sorted.get(i).getPricingPlan());
            if (t1 > t0) return true;
        }
        return false;
    }

    private boolean isLoyalist(Long userId, List<Subscription> subs) {
        if (subs.size() < 2) return false;
        Subscription latest = subs.stream().max(Comparator.comparing(Subscription::getCreatedAt)).orElse(null);
        if (latest == null || !"ACTIVE".equalsIgnoreCase(latest.getStatus()) || !latest.isAutoRenew()) return false;
        if (paymentRecordRepository.countByUserIdAndStatus(userId, "FAILED") > 0) return false;
        boolean yearlyOnce = subs.stream().anyMatch(s -> "YEARLY".equalsIgnoreCase(s.getBillingCycle()));
        return yearlyOnce;
    }

    private static int planTier(PricingPlan p) {
        if (p == null || p.getName() == null) return 0;
        String n = p.getName().toUpperCase();
        if (n.contains("PREMIUM")) return 3;
        if (n.contains("PRO")) return 2;
        if (n.contains("BASIC")) return 1;
        return 0;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDnaDto> calculateAllDna() {
        List<Long> ids = subscriptionRepository.findDistinctUserIds();
        if (ids == null) return new ArrayList<>();
        return ids.stream().map(this::calculateDna).collect(Collectors.toList());
    }

    // ── Intervention ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public InterventionDto getIntervention(Long userId) {
        ChurnRiskDto churn = calculateChurnRisk(userId);
        SubscriptionDnaDto dna = calculateDna(userId);
        Optional<Subscription> activeOpt = subscriptionRepository.findActiveByUserId(userId);
        Subscription active = activeOpt.orElse(null);

        int churnScore = churn.getScore();
        int daysLeft = active != null && active.getEndDate() != null
                ? (int) ChronoUnit.DAYS.between(LocalDate.now(), active.getEndDate().toLocalDate())
                : 999;

        if (active != null && active.getGracePeriodEnd() != null
                && active.getGracePeriodEnd().isAfter(LocalDateTime.now())) {
            return baseIntervention(userId, "PAYMENT_RECOVERY", "Récupérer le paiement échoué", 95,
                    "Grace period active — contacter immédiatement", dna.getArchetype(), churnScore);
        }
        if (churnScore >= 70 && daysLeft <= 14) {
            return baseIntervention(userId, "RETENTION_OFFER", "Offre de rétention urgente", 88,
                    "Haut risque de churn + expiration imminente", dna.getArchetype(), churnScore);
        }
        if ("GROWING".equals(dna.getArchetype()) && active != null && active.getPricingPlan() != null
                && !isPremiumName(active.getPricingPlan().getName())) {
            int months = consecutiveActiveMonths(active);
            int conf = Math.min(90, 75 + months * 2);
            String cur = active.getPricingPlan().getName();
            String next = nextPlanFromCatalog(cur);
            return baseIntervention(userId, "UPGRADE_PITCH",
                    "Proposer upgrade " + cur + " → " + next, conf,
                    "Comportement d'upgrade détecté sur " + months + " mois consécutifs",
                    dna.getArchetype(), churnScore);
        }
        if ("BARGAIN_HUNTER".equals(dna.getArchetype()) && daysLeft <= 30
                && promoCodeRepository.findEligibleActiveForUser(userId, LocalDateTime.now()).isEmpty()) {
            int disc = active != null && active.getPricingPlan() != null
                    ? recommendedDiscountPct(active.getPricingPlan().getName()) : 10;
            return baseIntervention(userId, "PROMO_SEND", "Envoyer promo " + disc + "%", 82,
                    "Profil sensible aux promotions — fenêtre optimale", dna.getArchetype(), churnScore);
        }
        if (churnScore >= 40 && active != null && !active.isAutoRenew()) {
            return baseIntervention(userId, "AUTORENEW_PUSH", "Activer le renouvellement automatique", 70,
                    "Activation du auto-renew réduirait le churn de ~60%", dna.getArchetype(), churnScore);
        }
        return baseIntervention(userId, "NO_ACTION", "Aucune action requise", 95,
                "Abonnement sain", dna.getArchetype(), churnScore);
    }

    private static InterventionDto baseIntervention(Long userId, String action, String label, int conf, String detail,
                                                    String dna, int churn) {
        return InterventionDto.builder()
                .userId(userId)
                .action(action)
                .label(label)
                .confidence(conf)
                .detail(detail)
                .dnaArchetype(dna)
                .churnScore(churn)
                .build();
    }

    private static boolean isPremiumName(String name) {
        return name != null && name.toUpperCase().contains("PREMIUM");
    }

    private String nextPlanFromCatalog(String current) {
        List<PricingPlan> active = pricingPlanRepository.findByIsActiveTrue();
        if (active.isEmpty()) {
            return nextPlanFallback(current);
        }
        int tCur = planTierFromName(current);
        return active.stream()
                .filter(p -> planTier(p) > tCur)
                .min(Comparator.comparingInt(SubscriptionAIService::planTier))
                .map(PricingPlan::getName)
                .orElseGet(() -> nextPlanFallback(current));
    }

    private static String nextPlanFallback(String current) {
        String u = current != null ? current.toUpperCase() : "";
        if (u.contains("BASIC")) return "PRO";
        if (u.contains("PRO")) return "PREMIUM";
        return "PREMIUM";
    }

    private static int planTierFromName(String name) {
        if (name == null) return 0;
        String u = name.toUpperCase();
        if (u.contains("PREMIUM")) return 3;
        if (u.contains("PRO")) return 2;
        if (u.contains("BASIC")) return 1;
        return 0;
    }

    private int consecutiveActiveMonths(Subscription active) {
        if (active.getStartDate() == null) return 1;
        long months = ChronoUnit.MONTHS.between(active.getStartDate().toLocalDate().withDayOfMonth(1),
                LocalDate.now().withDayOfMonth(1));
        return (int) Math.max(1, Math.min(12, months + 1));
    }

    @Transactional(readOnly = true)
    public List<InterventionDto> getAllInterventions() {
        List<Long> ids = subscriptionRepository.findDistinctUserIds();
        if (ids == null) return new ArrayList<>();
        return ids.stream()
                .map(this::getIntervention)
                .sorted(Comparator.comparingInt(InterventionDto::getConfidence).reversed()
                        .thenComparing(Comparator.comparingInt(InterventionDto::getChurnScore).reversed()))
                .collect(Collectors.toList());
    }

    // ── Contact window ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ContactWindowDto getOptimalContactWindow(Long userId) {
        ChurnRiskDto churn = calculateChurnRisk(userId);
        SubscriptionDnaDto dna = calculateDna(userId);
        Optional<Subscription> activeOpt = subscriptionRepository.findActiveByUserId(userId);
        if (activeOpt.isEmpty() || activeOpt.get().getEndDate() == null) {
            return ContactWindowDto.builder()
                    .userId(userId)
                    .contactInDays(0)
                    .contactDateLabel("N/A")
                    .channel("EMAIL_REMINDER")
                    .channelLabel("Rappel standard")
                    .avgResponseWindow(14)
                    .isOverdue(false)
                    .urgencyLevel("PLANNED")
                    .build();
        }
        Subscription active = activeOpt.get();
        LocalDateTime endDate = active.getEndDate();
        int avgWindow = computeAvgResponseWindow(userId, active, dna.getArchetype());

        LocalDateTime contactAt = endDate.minusDays(avgWindow);
        long rawDays = ChronoUnit.DAYS.between(LocalDate.now(), contactAt.toLocalDate());
        boolean overdue = rawDays < 0;
        int signedDays = (int) rawDays;

        String label;
        if (overdue) {
            label = "Contact en retard de " + Math.abs(rawDays) + " jours";
        } else if (rawDays == 0) {
            label = "Aujourd'hui";
        } else {
            label = "Dans " + rawDays + " jours";
        }

        String channel;
        String channelFr;
        if (churn.getScore() >= 70 || (active.getGracePeriodEnd() != null && active.getGracePeriodEnd().isAfter(LocalDateTime.now()))) {
            channel = "EMAIL_RETENTION";
            channelFr = "E-mail de rétention";
        } else if ("BARGAIN_HUNTER".equals(dna.getArchetype())) {
            channel = "EMAIL_PROMO";
            channelFr = "E-mail promotionnel";
        } else if ("GROWING".equals(dna.getArchetype())) {
            channel = "EMAIL_UPGRADE";
            channelFr = "E-mail upgrade";
        } else if (!active.isAutoRenew() && churn.getScore() < 70) {
            channel = "EMAIL_AUTORENEW";
            channelFr = "E-mail renouvellement auto";
        } else {
            channel = "EMAIL_REMINDER";
            channelFr = "Rappel abonnement";
        }

        String urgency = churn.getScore() >= 70 || overdue ? "URGENT"
                : (rawDays <= 3 ? "SOON" : "PLANNED");

        return ContactWindowDto.builder()
                .userId(userId)
                .contactInDays(signedDays)
                .contactDateLabel(label)
                .channel(channel)
                .channelLabel(channelFr)
                .avgResponseWindow(avgWindow)
                .isOverdue(overdue)
                .urgencyLevel(urgency)
                .build();
    }

    private int computeAvgResponseWindow(Long userId, Subscription active, String archetype) {
        List<PromoCode> promos = promoCodeRepository.findByCreatedByUserIdOrderByCreatedAtAsc(userId);
        List<Long> daysList = new ArrayList<>();
        LocalDateTime end = active.getEndDate();
        for (PromoCode p : promos) {
            int uses = p.getCurrentUses() == null ? 0 : p.getCurrentUses();
            if (uses < 1) continue;
            if (p.getCreatedAt() == null || end == null) continue;
            long d = ChronoUnit.DAYS.between(p.getCreatedAt().toLocalDate(), end.toLocalDate());
            if (d >= 0 && d < 400) daysList.add(d);
        }
        if (daysList.size() >= 2) {
            return (int) Math.round(daysList.stream().mapToLong(Long::longValue).average().orElse(14));
        }
        return defaultWindowForArchetype(archetype);
    }

    private static int defaultWindowForArchetype(String arch) {
        return switch (arch != null ? arch : "STANDARD") {
            case "LOYALIST" -> 14;
            case "BARGAIN_HUNTER" -> 10;
            case "VOLATILE" -> 20;
            case "GROWING" -> 21;
            case "AT_RISK" -> 7;
            default -> 14;
        };
    }

    @Transactional(readOnly = true)
    public List<ContactWindowDto> getAllContactWindows() {
        return subscriptionRepository.findAllActiveWithPlans().stream()
                .map(Subscription::getUserId)
                .distinct()
                .map(this::getOptimalContactWindow)
                .sorted(Comparator.comparing(ContactWindowDto::isOverdue).reversed()
                        .thenComparingInt(ContactWindowDto::getContactInDays))
                .collect(Collectors.toList());
    }

    // ── Momentum ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MomentumDto calculateMomentum() {
        List<Subscription> all = subscriptionRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        List<WeeklySnapshotDto> history = new ArrayList<>();
        int[] net = new int[4];
        String[] labels = { "Il y a 3 sem.", "Il y a 2 sem.", "Semaine dernière", "Semaine en cours" };
        for (int w = 3; w >= 0; w--) {
            LocalDateTime from = now.minusDays((long) (w + 1) * 7);
            LocalDateTime to = now.minusDays((long) w * 7);
            int idx = 3 - w;
            int n = countNewSubs(all, from, to);
            int c = countCancellations(all, from, to);
            int ng = n - c;
            net[idx] = ng;
            history.add(WeeklySnapshotDto.builder()
                    .weekLabel(labels[idx])
                    .newSubs(n)
                    .cancellations(c)
                    .netGrowth(ng)
                    .build());
        }

        int nw0 = net[3];
        int nw1 = net[2];
        String trend;
        String trendFr;
        if (nw0 < 0) {
            trend = "NEGATIVE";
            trendFr = "Tendance négative";
        } else if (nw0 > nw1) {
            trend = "ACCELERATING";
            trendFr = "Accélération";
        } else if (nw0 == nw1) {
            trend = "STABLE";
            trendFr = "Stable";
        } else {
            trend = "DECELERATING";
            trendFr = "Décélération";
        }
        String alert = "NEGATIVE".equals(trend)
                ? "Attention : la croissance nette est négative sur la semaine en cours."
                : null;

        List<PlanMomentumDto> perPlan = new ArrayList<>();
        for (String pname : List.of("BASIC", "PRO", "PREMIUM")) {
            int n0 = countNewForPlan(all, pname, now.minusDays(7), now);
            int n1 = countNewForPlan(all, pname, now.minusDays(14), now.minusDays(7));
            int c0 = countCancelForPlan(all, pname, now.minusDays(7), now);
            int c1 = countCancelForPlan(all, pname, now.minusDays(14), now.minusDays(7));
            int g0 = n0 - c0;
            int g1 = n1 - c1;
            String tr = g0 > g1 ? "UP" : (g0 < g1 ? "DOWN" : "STABLE");
            String arrow = "UP".equals(tr) ? "↑" : ("DOWN".equals(tr) ? "↓" : "→");
            perPlan.add(PlanMomentumDto.builder()
                    .planName(pname)
                    .trend(tr)
                    .netGrowth(g0)
                    .trendArrow(arrow)
                    .build());
        }

        return MomentumDto.builder()
                .netGrowthThisWeek(net[3])
                .netGrowthLastWeek(net[2])
                .globalTrend(trend)
                .globalTrendLabel(trendFr)
                .alertMessage(alert)
                .weeklyHistory(history)
                .perPlan(perPlan)
                .build();
    }

    private static int countNewSubs(List<Subscription> all, LocalDateTime from, LocalDateTime to) {
        return (int) all.stream()
                .filter(s -> s.getCreatedAt() != null
                        && !s.getCreatedAt().isBefore(from)
                        && s.getCreatedAt().isBefore(to)
                        && !"CANCELLED".equalsIgnoreCase(s.getStatus()))
                .count();
    }

    private static int countCancellations(List<Subscription> all, LocalDateTime from, LocalDateTime to) {
        return (int) all.stream()
                .filter(s -> "CANCELLED".equalsIgnoreCase(s.getStatus())
                        && s.getEndDate() != null
                        && !s.getEndDate().isBefore(from)
                        && s.getEndDate().isBefore(to))
                .count();
    }

    private static int countNewForPlan(List<Subscription> all, String planName, LocalDateTime from, LocalDateTime to) {
        return (int) all.stream()
                .filter(s -> s.getPricingPlan() != null
                        && s.getPricingPlan().getName() != null
                        && s.getPricingPlan().getName().toUpperCase().contains(planName)
                        && s.getCreatedAt() != null
                        && !s.getCreatedAt().isBefore(from)
                        && s.getCreatedAt().isBefore(to)
                        && !"CANCELLED".equalsIgnoreCase(s.getStatus()))
                .count();
    }

    private static int countCancelForPlan(List<Subscription> all, String planName, LocalDateTime from, LocalDateTime to) {
        return (int) all.stream()
                .filter(s -> s.getPricingPlan() != null
                        && s.getPricingPlan().getName() != null
                        && s.getPricingPlan().getName().toUpperCase().contains(planName)
                        && "CANCELLED".equalsIgnoreCase(s.getStatus())
                        && s.getEndDate() != null
                        && !s.getEndDate().isBefore(from)
                        && s.getEndDate().isBefore(to))
                .count();
    }

    // ── Advanced intelligence (v2) ────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CohortSurvivalDTO> cohortSurvival() {
        List<Subscription> all = subscriptionRepository.findAll();
        Map<YearMonth, List<Subscription>> byMonth = all.stream()
                .filter(s -> s.getCreatedAt() != null)
                .collect(Collectors.groupingBy(s -> YearMonth.from(s.getCreatedAt())));
        List<CohortSurvivalDTO> out = new ArrayList<>();
        for (Map.Entry<YearMonth, List<Subscription>> e : byMonth.entrySet()) {
            List<Subscription> cohort = e.getValue();
            int total = cohort.size();
            double s30 = cohortSurvivalRate(cohort, 30, total);
            double s60 = cohortSurvivalRate(cohort, 60, total);
            double s90 = cohortSurvivalRate(cohort, 90, total);
            out.add(CohortSurvivalDTO.builder()
                    .cohortMonth(e.getKey().toString())
                    .totalUsers(total)
                    .survival30(round2(s30))
                    .survival60(round2(s60))
                    .survival90(round2(s90))
                    .build());
        }
        out.sort(Comparator.comparing(CohortSurvivalDTO::getCohortMonth).reversed());
        return out;
    }

    private static double cohortSurvivalRate(List<Subscription> cohort, int days, int total) {
        if (total == 0) return 0;
        long eligible = cohort.stream()
                .filter(s -> !s.getCreatedAt().plusDays(days).isAfter(LocalDateTime.now()))
                .count();
        if (eligible == 0) return 0;
        long survived = cohort.stream()
                .filter(s -> !s.getCreatedAt().plusDays(days).isAfter(LocalDateTime.now()))
                .filter(s -> survivedAtDay(s, days))
                .count();
        return 100.0 * survived / eligible;
    }

    private static boolean survivedAtDay(Subscription s, int days) {
        LocalDateTime start = s.getCreatedAt();
        LocalDateTime point = start.plusDays(days);
        if (point.isAfter(LocalDateTime.now())) {
            return false;
        }
        if ("ACTIVE".equalsIgnoreCase(s.getStatus())) {
            return s.getEndDate() == null || s.getEndDate().isAfter(point);
        }
        if (s.getEndDate() == null) {
            return false;
        }
        return s.getEndDate().isAfter(point);
    }

    @Transactional(readOnly = true)
    public MonteCarloForecastDTO monteCarloRevenue() {
        List<Subscription> active = subscriptionRepository.findAllActiveWithPlans();
        Map<Long, Integer> churnCache = new HashMap<>();
        for (Subscription s : active) {
            churnCache.putIfAbsent(s.getUserId(), calculateChurnRisk(s.getUserId()).getScore());
        }
        double currentMrr = active.stream().mapToDouble(SubscriptionAIService::monthlyMrr).sum();
        final int SIMS = 1000;
        double[] sims = new double[SIMS];
        Random rnd = new Random();
        for (int i = 0; i < SIMS; i++) {
            double mrr = 0;
            for (Subscription s : active) {
                int score = churnCache.getOrDefault(s.getUserId(), 0);
                double p = Math.min(0.99, Math.max(0.01, score / 100.0));
                if (rnd.nextDouble() >= p) {
                    mrr += monthlyMrr(s);
                }
            }
            sims[i] = mrr;
        }
        Arrays.sort(sims);
        return MonteCarloForecastDTO.builder()
                .p10(sims[(int) Math.floor(SIMS * 0.10)])
                .p50(sims[(int) Math.floor(SIMS * 0.50)])
                .p90(sims[(int) Math.floor(SIMS * 0.90)])
                .currentMRR(round2(currentMrr))
                .minSim(sims[0])
                .maxSim(sims[SIMS - 1])
                .simulations(SIMS)
                .build();
    }

    private static double monthlyMrr(Subscription s) {
        PricingPlan p = s.getPricingPlan();
        if (p == null) return 0;
        if (s.getBillingCycle() != null && s.getBillingCycle().equalsIgnoreCase("YEARLY")) {
            Double y = p.getYearlyPrice();
            return y != null ? y / 12.0 : 0;
        }
        Double m = p.getMonthlyPrice();
        return m != null ? m : 0;
    }

    @Transactional(readOnly = true)
    public List<LTVUserDTO> ltvDecay() {
        List<Long> userIds = subscriptionRepository.findDistinctUserIds();
        if (userIds == null) return new ArrayList<>();
        List<LTVUserDTO> rows = new ArrayList<>();
        for (Long uid : userIds) {
            Optional<Subscription> active = subscriptionRepository.findActiveByUserId(uid);
            Subscription sub = active.orElseGet(() ->
                    subscriptionRepository.findByUserIdOrderByCreatedAtAsc(uid).stream()
                            .reduce((a, b) -> b)
                            .orElse(null));
            if (sub == null || sub.getPricingPlan() == null) continue;
            int score = calculateChurnRisk(uid).getScore();
            double expected = score > 0 ? Math.min(48.0, 100.0 / score) : 48.0;
            double price = monthlyMrr(sub);
            double ltv = price * expected;
            String planName = sub.getPricingPlan().getName() != null ? sub.getPricingPlan().getName() : "—";
            rows.add(LTVUserDTO.builder()
                    .userId(uid)
                    .plan(planName)
                    .planPrice(round2(price))
                    .churnScore(score)
                    .expectedMonths(round2(expected))
                    .ltv(round2(ltv))
                    .build());
        }
        rows.sort(Comparator.comparingDouble(LTVUserDTO::getLtv).reversed());
        return rows;
    }

    @Transactional
    public InterventionLog recordInterventionLog(InterventionLogRequestDTO req) {
        if (req.getUserId() == null || req.getActionType() == null || req.getOutcome() == null) {
            throw new IllegalArgumentException("userId, actionType et outcome sont requis");
        }
        InterventionLog log = new InterventionLog();
        log.setUserId(req.getUserId());
        log.setActionType(req.getActionType());
        log.setOutcome(req.getOutcome().toUpperCase());
        log.setRecommendedAt(req.getRecommendedAt() != null ? req.getRecommendedAt() : LocalDateTime.now());
        log.setExecutedAt(req.getExecutedAt() != null ? req.getExecutedAt() : LocalDateTime.now());
        log.setConfidenceAtTime(req.getConfidenceAtTime());
        return interventionLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<ActionAccuracyDTO> interventionAccuracy() {
        List<InterventionLog> all = interventionLogRepository.findAll();
        Map<String, List<InterventionLog>> byAction = all.stream()
                .filter(l -> "SUCCESS".equalsIgnoreCase(l.getOutcome()) || "MISS".equalsIgnoreCase(l.getOutcome()))
                .collect(Collectors.groupingBy(InterventionLog::getActionType));
        List<ActionAccuracyDTO> out = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime t14 = now.minusDays(14);
        LocalDateTime t28 = now.minusDays(28);
        for (Map.Entry<String, List<InterventionLog>> e : byAction.entrySet()) {
            String action = e.getKey();
            List<InterventionLog> list = e.getValue();
            int total = list.size();
            int successes = (int) list.stream().filter(l -> "SUCCESS".equalsIgnoreCase(l.getOutcome())).count();
            double acc = total > 0 ? (100.0 * successes / total) : 0;
            double recent = successRateBetween(list, t14, now);
            double older = successRateBetween(list, t28, t14);
            String trend = "STABLE";
            if (older >= 0 && recent - older > 5) trend = "UP";
            else if (older >= 0 && older - recent > 5) trend = "DOWN";
            out.add(ActionAccuracyDTO.builder()
                    .actionType(action)
                    .total(total)
                    .successes(successes)
                    .accuracy(round2(acc))
                    .trend(trend)
                    .build());
        }
        out.sort(Comparator.comparing(ActionAccuracyDTO::getActionType));
        return out;
    }

    private static double successRateBetween(List<InterventionLog> list, LocalDateTime from, LocalDateTime to) {
        List<InterventionLog> slice = list.stream()
                .filter(l -> l.getExecutedAt() != null
                        && !l.getExecutedAt().isBefore(from)
                        && l.getExecutedAt().isBefore(to))
                .filter(l -> "SUCCESS".equalsIgnoreCase(l.getOutcome()) || "MISS".equalsIgnoreCase(l.getOutcome()))
                .collect(Collectors.toList());
        if (slice.isEmpty()) return -1;
        long ok = slice.stream().filter(l -> "SUCCESS".equalsIgnoreCase(l.getOutcome())).count();
        return 100.0 * ok / slice.size();
    }

    @Transactional(readOnly = true)
    public List<UpgradeCandidateDTO> upgradePressure() {
        int maxTier = pricingPlanRepository.findByIsActiveTrue().stream()
                .mapToInt(SubscriptionAIService::planTier)
                .max()
                .orElse(3);
        List<UpgradeCandidateDTO> out = new ArrayList<>();
        for (Subscription s : subscriptionRepository.findAllActiveWithPlans()) {
            if (s.getPricingPlan() == null) continue;
            if (planTier(s.getPricingPlan()) >= maxTier) continue;
            long months = ChronoUnit.MONTHS.between(
                    s.getStartDate() != null ? s.getStartDate().toLocalDate().withDayOfMonth(1) : LocalDate.now(),
                    LocalDate.now().withDayOfMonth(1));
            int tenure = (int) Math.max(0, Math.min(120, months + 1));
            int score = (int) Math.round(Math.min(100, tenure * 0.4 + (s.isAutoRenew() ? 30 : 0)
                    + ("YEARLY".equalsIgnoreCase(s.getBillingCycle()) ? 30 : 0)));
            String pitch = score >= 70
                    ? "Proposer migration vers le plan supérieur avec démo personnalisée"
                    : (score >= 40
                    ? "Mettre en avant les fonctionnalités premium par e-mail ciblé"
                    : "Newsletter soft sur les bénéfices du niveau supérieur");
            out.add(UpgradeCandidateDTO.builder()
                    .userId(s.getUserId())
                    .plan(s.getPricingPlan().getName())
                    .tenureMonths(tenure)
                    .autoRenew(s.isAutoRenew())
                    .upgradeScore(score)
                    .recommendedPitch(pitch)
                    .build());
        }
        out.sort(Comparator.comparingInt(UpgradeCandidateDTO::getUpgradeScore).reversed());
        return out;
    }

    @Transactional(readOnly = true)
    public List<AnomalyDTO> behavioralAnomalies() {
        List<Subscription> subs = subscriptionRepository.findAll();
        List<PaymentRecord> payments = paymentRecordRepository.findAll();
        LocalDate today = LocalDate.now();
        List<Double> seriesNew = new ArrayList<>();
        List<Double> seriesCancel = new ArrayList<>();
        List<Double> seriesFail = new ArrayList<>();
        for (int d = 1; d <= 28; d++) {
            LocalDate day = today.minusDays(d);
            seriesNew.add((double) countNewSubsOnDay(subs, day));
            seriesCancel.add((double) countCancellationsOnDay(subs, day));
            seriesFail.add((double) countFailedPaymentsOnDay(payments, day));
        }
        double todayNew = countNewSubsOnDay(subs, today);
        double todayCancel = countCancellationsOnDay(subs, today);
        double todayFail = countFailedPaymentsOnDay(payments, today);
        List<AnomalyDTO> out = new ArrayList<>();
        out.add(buildAnomaly("NEW_SUBS", "Nouveaux abonnements", todayNew, seriesNew));
        out.add(buildAnomaly("CANCELLATIONS", "Annulations", todayCancel, seriesCancel));
        out.add(buildAnomaly("PAYMENT_FAILURES", "Paiements échoués", todayFail, seriesFail));
        return out;
    }

    private static AnomalyDTO buildAnomaly(String key, String label, double todayVal, List<Double> history28) {
        double mu = history28.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double sigma = stddev(history28);
        double z = sigma > 1e-9 ? (todayVal - mu) / sigma : 0;
        boolean anom = Math.abs(z) > 2.0;
        String sev = Math.abs(z) <= 2 ? "NORMAL" : (Math.abs(z) <= 3 ? "WARNING" : "CRITICAL");
        return AnomalyDTO.builder()
                .metric(key)
                .metricLabel(label)
                .todayValue(round2(todayVal))
                .mean(round2(mu))
                .stddev(round2(sigma))
                .zScore(round2(z))
                .anomaly(anom)
                .severity(sev)
                .build();
    }

    private static double stddev(List<Double> vals) {
        if (vals.isEmpty()) return 0;
        double mean = vals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double var = vals.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0);
        return Math.sqrt(var);
    }

    private static int countNewSubsOnDay(List<Subscription> all, LocalDate day) {
        return (int) all.stream()
                .filter(s -> s.getCreatedAt() != null && s.getCreatedAt().toLocalDate().equals(day))
                .count();
    }

    private static int countCancellationsOnDay(List<Subscription> all, LocalDate day) {
        return (int) all.stream()
                .filter(s -> "CANCELLED".equalsIgnoreCase(s.getStatus())
                        && s.getEndDate() != null
                        && s.getEndDate().toLocalDate().equals(day))
                .count();
    }

    private static int countFailedPaymentsOnDay(List<PaymentRecord> all, LocalDate day) {
        return (int) all.stream()
                .filter(p -> "FAILED".equalsIgnoreCase(p.getStatus())
                        && p.getCreatedAt() != null
                        && p.getCreatedAt().toLocalDate().equals(day))
                .count();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private record ScoredReason(int points, String label) {}
}
