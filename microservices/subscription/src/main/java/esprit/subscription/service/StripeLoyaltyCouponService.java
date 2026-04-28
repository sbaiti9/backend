package esprit.subscription.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Coupon;
import com.stripe.model.PromotionCode;
import com.stripe.param.CouponCreateParams;
import com.stripe.param.PromotionCodeCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class StripeLoyaltyCouponService {
    private static final Logger log = LoggerFactory.getLogger(StripeLoyaltyCouponService.class);

    @Value("${stripe.secret.key:}")
    private String stripeSecretKey;

    public record LoyaltyCouponResult(String couponId, String promotionCodeId, String code) {}

    public LoyaltyCouponResult createMilestoneCoupon(Long userId, int milestoneMonth, int discountPercent) {
        if (stripeSecretKey == null || stripeSecretKey.isBlank() || stripeSecretKey.contains("YOUR_")) {
            throw new IllegalStateException("Stripe is not configured (stripe.secret.key)");
        }
        Stripe.apiKey = stripeSecretKey;

        String couponId = ("skillio-loyalty-" + milestoneMonth + "m-" + discountPercent + "off")
                .toLowerCase(Locale.ROOT);
        try {
            Coupon coupon;
            try {
                coupon = Coupon.retrieve(couponId);
            } catch (Exception ignored) {
                CouponCreateParams cp = CouponCreateParams.builder()
                        .setId(couponId)
                        .setDuration(CouponCreateParams.Duration.ONCE)
                        .setPercentOff(BigDecimal.valueOf(discountPercent))
                        .setName("Skillio Loyalty " + milestoneMonth + " months")
                        .build();
                coupon = Coupon.create(cp);
            }

            Map<String, String> metadata = new HashMap<>();
            metadata.put("program", "LOYALTY");
            metadata.put("milestoneMonth", String.valueOf(milestoneMonth));
            metadata.put("userId", String.valueOf(userId));

            PromotionCodeCreateParams pp = PromotionCodeCreateParams.builder()
                    .setCoupon(coupon.getId())
                    .setMaxRedemptions(1L)
                    .putAllMetadata(metadata)
                    .build();
            PromotionCode createdPromotion = PromotionCode.create(pp);
            return new LoyaltyCouponResult(coupon.getId(), createdPromotion.getId(), createdPromotion.getCode());
        } catch (StripeException e) {
            log.warn("Stripe loyalty coupon creation failed (user={} milestone={}): {}", userId, milestoneMonth, e.getMessage());
            throw new IllegalStateException("Stripe loyalty coupon creation failed: " + e.getMessage(), e);
        }
    }
}

