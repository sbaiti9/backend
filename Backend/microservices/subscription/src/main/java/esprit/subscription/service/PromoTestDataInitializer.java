package esprit.subscription.service;

import esprit.subscription.entity.PromoAudience;
import esprit.subscription.entity.PromoCode;
import esprit.subscription.entity.PromoDiscountType;
import esprit.subscription.repository.PromoCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Inserts demo promo codes when missing (ddl-auto=update). Safe to run on every startup.
 */
@Component
@Order(50)
public class PromoTestDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PromoTestDataInitializer.class);

    public static final String DEMO_CODE_PERCENT = "TEST-SKILLIO-2026";
    public static final String DEMO_CODE_FIXED = "TEST-FIXED-5EUR";

    private final PromoCodeRepository promoCodeRepository;

    public PromoTestDataInitializer(PromoCodeRepository promoCodeRepository) {
        this.promoCodeRepository = promoCodeRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedPercentDemo();
        seedFixedDemo();
    }

    private void seedPercentDemo() {
        if (promoCodeRepository.findByCodeNormalized(DEMO_CODE_PERCENT).isPresent()) {
            return;
        }
        PromoCode p = new PromoCode();
        p.setCode(DEMO_CODE_PERCENT);
        p.setDiscountType(PromoDiscountType.PERCENT);
        p.setDiscountPercent(15d);
        p.setApplicableAudience(PromoAudience.ALL);
        p.setCreatedByUserId(1L);
        p.setMaxUses(10_000);
        p.setCurrentUses(0);
        p.setExpirationDate(LocalDateTime.now().plusYears(1));
        p.setActive(true);
        p.setMaxRedemptionsPerUser(3);
        promoCodeRepository.save(p);
        log.info("Demo promo inserted — code={} (15% OFF, ALL audiences, max 3 uses per user)", DEMO_CODE_PERCENT);
    }

    private void seedFixedDemo() {
        if (promoCodeRepository.findByCodeNormalized(DEMO_CODE_FIXED).isPresent()) {
            return;
        }
        PromoCode p = new PromoCode();
        p.setCode(DEMO_CODE_FIXED);
        p.setDiscountType(PromoDiscountType.FIXED_AMOUNT);
        p.setDiscountPercent(0d);
        p.setDiscountAmount(5d);
        p.setApplicableAudience(PromoAudience.ALL);
        p.setCreatedByUserId(1L);
        p.setMaxUses(10_000);
        p.setCurrentUses(0);
        p.setExpirationDate(LocalDateTime.now().plusYears(1));
        p.setActive(true);
        p.setMaxRedemptionsPerUser(5);
        promoCodeRepository.save(p);
        log.info("Demo promo inserted — code={} (5 EUR off, ALL audiences)", DEMO_CODE_FIXED);
    }
}
