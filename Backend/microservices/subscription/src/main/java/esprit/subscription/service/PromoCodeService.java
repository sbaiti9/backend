package esprit.subscription.service;

import esprit.subscription.DTO.PromoValidationDto;
import esprit.subscription.DTO.CompanyDto;
import esprit.subscription.client.EntrepriseClient;
import esprit.subscription.entity.PromoAudience;
import esprit.subscription.entity.PromoCode;
import esprit.subscription.entity.PromoDiscountType;
import esprit.subscription.entity.PromoRedemption;
import esprit.subscription.repository.PromoCodeRepository;
import esprit.subscription.repository.PromoRedemptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PromoCodeService {

    private static final Logger log = LoggerFactory.getLogger(PromoCodeService.class);

    public static final String BIRTHDAY_CODE_PREFIX = "BDAY-";
    private static final int BIRTHDAY_DISCOUNT_PERCENT = 20;
    private static final int BIRTHDAY_VALIDITY_DAYS = 30;

    private final PromoCodeRepository promoCodeRepository;
    private final PromoRedemptionRepository promoRedemptionRepository;
    private final EntrepriseClient entrepriseClient;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    public PromoCodeService(
            PromoCodeRepository promoCodeRepository,
            PromoRedemptionRepository promoRedemptionRepository,
            EntrepriseClient entrepriseClient,
            EmailService emailService) {
        this.promoCodeRepository = promoCodeRepository;
        this.promoRedemptionRepository = promoRedemptionRepository;
        this.entrepriseClient = entrepriseClient;
        this.emailService = emailService;
    }

    public static String normalizeCodeInput(String raw) {
        return raw == null ? "" : raw.trim();
    }

    /**
     * Maps API roles (ETUDIANT / ENTERPRISE / …) to {@link PromoAudience} rules.
     */
    public static PromoAudience audienceFromUserRole(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            return PromoAudience.ALL;
        }
        String u = userRole.trim().toUpperCase(Locale.ROOT);
        if (u.contains("ETUDIANT") || "STUDENT".equals(u)) {
            return PromoAudience.STUDENT;
        }
        if (u.contains("ENTREPRISE") || "ENTERPRISE".equals(u) || "COMPANY".equals(u)) {
            return PromoAudience.ENTERPRISE;
        }
        return PromoAudience.STUDENT;
    }

    @Transactional
    public PromoCode createAiTargetingPromo(Long userId, int discountPercent) {
        if (discountPercent < 1 || discountPercent > 50) {
            throw new IllegalArgumentException("discount must be between 1 and 50");
        }
        String code = "AI-TGT-" + randomHex(8);
        while (promoCodeRepository.findByCodeNormalized(code).isPresent()) {
            code = "AI-TGT-" + randomHex(8);
        }
        PromoCode p = new PromoCode();
        p.setCode(code);
        p.setDiscountType(PromoDiscountType.PERCENT);
        p.setDiscountPercent((double) discountPercent);
        p.setCreatedByUserId(userId);
        p.setMaxUses(1);
        p.setCurrentUses(0);
        p.setExpirationDate(LocalDateTime.now().plusDays(30));
        p.setActive(true);
        p.setApplicableAudience(PromoAudience.ALL);
        p.setMaxRedemptionsPerUser(1);
        PromoCode saved = promoCodeRepository.save(p);
        log.info("AI targeting promo created — code={} userId={} discount={}%", saved.getCode(), userId, discountPercent);
        try {
            CompanyDto co = entrepriseClient.getCompanyByUserId(userId);
            if (co != null && co.getEmail() != null) {
                emailService.sendAiTargetingPromoEmail(
                        co.getEmail(),
                        co.getName() != null ? co.getName() : "Client",
                        saved.getCode(),
                        discountPercent);
            }
        } catch (Exception e) {
            log.warn("AI promo email not sent for userId={}", userId, e);
        }
        return saved;
    }

    @Transactional
    public PromoCode createPromoCode(Long userId, PromoCode promoCode) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        boolean recentExists = promoCodeRepository.existsByCreatedByUserIdSince(userId, thirtyDaysAgo);
        if (recentExists) {
            throw new IllegalStateException("User " + userId + " already created a promo code within the last 30 days.");
        }
        promoCode.setCreatedByUserId(userId);
        if (promoCode.getDiscountType() == null) {
            promoCode.setDiscountType(PromoDiscountType.PERCENT);
        }
        if (promoCode.getApplicableAudience() == null) {
            promoCode.setApplicableAudience(PromoAudience.ALL);
        }
        PromoCode saved = promoCodeRepository.save(promoCode);
        log.info(
                "PromoCode created — code={} userId={} type={}",
                saved.getCode(),
                userId,
                saved.effectiveDiscountType());
        return saved;
    }

    /**
     * Legacy: basic global validation only (no per-user / audience / plan).
     */
    public Optional<PromoCode> validate(String code) {
        String n = normalizeCodeInput(code);
        if (n.isEmpty()) {
            return Optional.empty();
        }
        return promoCodeRepository.findByCodeNormalized(n).filter(this::passesGlobalRules);
    }

    public Optional<PromoCode> findNormalized(String code) {
        String n = normalizeCodeInput(code);
        if (n.isEmpty()) {
            return Optional.empty();
        }
        return promoCodeRepository.findByCodeNormalized(n);
    }

    private boolean passesGlobalRules(PromoCode p) {
        if (!p.isActive()) {
            return false;
        }
        if (p.getExpirationDate() != null && p.getExpirationDate().isBefore(LocalDateTime.now())) {
            return false;
        }
        if (p.getMaxUses() != null && p.getCurrentUses() != null && p.getCurrentUses() >= p.getMaxUses()) {
            return false;
        }
        return true;
    }

    public PromoValidationDto validateDetailed(
            String rawCode,
            Long userId,
            String userRole,
            Long planId,
            String billingCycle,
            Double baseAmountEur) {
        String n = normalizeCodeInput(rawCode);
        if (n.isEmpty()) {
            return PromoValidationDto.builder().valid(false).message("Code requis").build();
        }
        Optional<PromoCode> opt = promoCodeRepository.findByCodeNormalized(n);
        if (opt.isEmpty()) {
            return PromoValidationDto.builder().valid(false).message("Code promo inconnu").build();
        }
        PromoCode p = opt.get();
        if (!passesGlobalRules(p)) {
            String msg = !p.isActive()
                    ? "Code inactif"
                    : (p.getExpirationDate() != null && p.getExpirationDate().isBefore(LocalDateTime.now()))
                            ? "Code expiré"
                            : "Code épuisé";
            return PromoValidationDto.builder().valid(false).message(msg).build();
        }
        if (userId != null && p.getCreatedByUserId() != null && !p.getCreatedByUserId().equals(userId)) {
            boolean creatorMustMatch =
                    (p.getMaxUses() != null && p.getMaxUses() == 1)
                            || (p.getCode() != null
                                    && (p.getCode().startsWith("AI-TGT-")
                                            || p.getCode().startsWith(BIRTHDAY_CODE_PREFIX)));
            if (creatorMustMatch) {
                return PromoValidationDto.builder()
                        .valid(false)
                        .message("Ce code n'est pas associé à ce compte.")
                        .build();
            }
        }
        PromoAudience need = p.getApplicableAudience() != null ? p.getApplicableAudience() : PromoAudience.ALL;
        if (need != PromoAudience.ALL && userRole != null) {
            PromoAudience actual = audienceFromUserRole(userRole);
            if (need == PromoAudience.STUDENT && actual != PromoAudience.STUDENT) {
                return PromoValidationDto.builder()
                        .valid(false)
                        .message("Ce code est réservé aux comptes étudiants.")
                        .build();
            }
            if (need == PromoAudience.ENTERPRISE && actual != PromoAudience.ENTERPRISE) {
                return PromoValidationDto.builder()
                        .valid(false)
                        .message("Ce code est réservé aux comptes entreprise.")
                        .build();
            }
        }
        if (planId != null && !allowedPlanIdsOk(p, planId)) {
            return PromoValidationDto.builder()
                    .valid(false)
                    .message("Ce code ne s'applique pas à ce plan.")
                    .build();
        }
        if (userId != null && !perUserLimitOk(p, userId)) {
            return PromoValidationDto.builder()
                    .valid(false)
                    .message("Vous avez déjà utilisé ce code le nombre maximum de fois.")
                    .build();
        }

        PromoValidationDto.PromoValidationDtoBuilder b = PromoValidationDto.builder()
                .valid(true)
                .code(p.getCode())
                .discountType(p.effectiveDiscountType().name())
                .currency("EUR");

        if (p.effectiveDiscountType() == PromoDiscountType.PERCENT) {
            b.discountPercent(p.getDiscountPercent());
        } else {
            b.discountAmount(p.getDiscountAmount() != null ? p.getDiscountAmount() : 0d);
        }

        if (baseAmountEur != null && baseAmountEur > 0) {
            double fin = applyDiscountToAmount(baseAmountEur, p);
            b.originalAmount(round2(baseAmountEur)).finalAmount(round2(fin));
        }

        return b.build();
    }

    private boolean allowedPlanIdsOk(PromoCode p, long planId) {
        String raw = p.getAllowedPlanIds();
        if (raw == null || raw.isBlank()) {
            return true;
        }
        Set<Long> ids = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(x -> x != null)
                .collect(Collectors.toSet());
        return !ids.isEmpty() && ids.contains(planId);
    }

    private boolean perUserLimitOk(PromoCode p, long userId) {
        Integer maxPu = p.getMaxRedemptionsPerUser();
        if (maxPu == null) {
            return true;
        }
        long used = promoRedemptionRepository.countByPromoCode_IdAndUserId(p.getId(), userId);
        return used < maxPu;
    }

    public double applyDiscountToAmount(double baseAmountEur, PromoCode p) {
        if (p.effectiveDiscountType() == PromoDiscountType.FIXED_AMOUNT) {
            double off = p.getDiscountAmount() != null ? p.getDiscountAmount() : 0d;
            return Math.max(0d, baseAmountEur - off);
        }
        double pct = p.getDiscountPercent() != null ? p.getDiscountPercent() : 0d;
        return baseAmountEur * (1d - pct / 100d);
    }

    private static double round2(double v) {
        return Math.round(v * 100d) / 100d;
    }

    /**
     * After successful payment: increment global uses and record per-user redemption.
     */
    @Transactional
    public void recordUsage(PromoCode promoCode, Long userId, Long subscriptionId) {
        int uses = promoCode.getCurrentUses() == null ? 0 : promoCode.getCurrentUses();
        promoCode.setCurrentUses(uses + 1);
        promoCodeRepository.save(promoCode);
        if (userId != null) {
            PromoRedemption r = PromoRedemption.builder()
                    .promoCode(promoCode)
                    .userId(userId)
                    .subscriptionId(subscriptionId)
                    .redeemedAt(LocalDateTime.now())
                    .build();
            promoRedemptionRepository.save(r);
        }
    }

    /** @deprecated use {@link #recordUsage(PromoCode, Long, Long)} */
    @Deprecated
    @Transactional
    public void recordUsage(PromoCode promoCode) {
        recordUsage(promoCode, null, null);
    }

    public List<PromoCode> findAll() {
        return promoCodeRepository.findAll();
    }

    public Optional<PromoCode> findById(Long id) {
        return promoCodeRepository.findById(id);
    }

    public Optional<PromoCode> getActiveUnusedPromoForUser(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<PromoCode> personal = promoCodeRepository.findEligibleActiveForUser(userId, now);
        Optional<PromoCode> firstPersonal =
                personal.stream().filter(p -> perUserLimitOk(p, userId)).findFirst();
        if (firstPersonal.isPresent()) {
            return firstPersonal;
        }
        /** System / seeded campaign owner id (see demo promos {@code createdByUserId = 1}). */
        long systemOwnerId = 1L;
        List<PromoCode> system =
                promoCodeRepository.findEligibleSystemCampaigns(systemOwnerId, PromoAudience.ALL, now);
        return system.stream().filter(p -> perUserLimitOk(p, userId)).findFirst();
    }

    @Transactional
    public Optional<PromoCode> generateBirthdayPromoForUser(Long userId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        if (promoCodeRepository.existsByCreatedByUserIdSince(userId, thirtyDaysAgo)) {
            log.debug("Birthday promo skipped — user {} had a promo created in the last 30 days", userId);
            return Optional.empty();
        }

        String code = BIRTHDAY_CODE_PREFIX + randomHex(8);
        while (promoCodeRepository.findByCodeNormalized(code).isPresent()) {
            code = BIRTHDAY_CODE_PREFIX + randomHex(8);
        }

        PromoCode p = new PromoCode();
        p.setCode(code);
        p.setDiscountType(PromoDiscountType.PERCENT);
        p.setDiscountPercent((double) BIRTHDAY_DISCOUNT_PERCENT);
        p.setCreatedByUserId(userId);
        p.setMaxUses(1);
        p.setCurrentUses(0);
        p.setExpirationDate(LocalDateTime.now().plusDays(BIRTHDAY_VALIDITY_DAYS));
        p.setActive(true);
        p.setApplicableAudience(PromoAudience.ALL);
        p.setMaxRedemptionsPerUser(1);

        PromoCode saved = promoCodeRepository.save(p);
        log.info("Birthday promo generated — code={} userId={}", saved.getCode(), userId);
        return Optional.of(saved);
    }

    private String randomHex(int len) {
        StringBuilder sb = new StringBuilder(len);
        String chars = "0123456789ABCDEF";
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
