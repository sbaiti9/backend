package esprit.subscription.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.param.SubscriptionUpdateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * Optional Stripe Billing sync when {@code stripeSubscriptionId} is set (Checkout in subscription mode).
 * Legacy one-time Checkout ({@code mode=payment}) has no Stripe subscription — pause is DB-only.
 */
@Service
public class StripeSubscriptionSyncService {

    private static final Logger log = LoggerFactory.getLogger(StripeSubscriptionSyncService.class);

    @Value("${stripe.secret.key:}")
    private String stripeSecretKey;

    public void pauseOnStripeIfPresent(String stripeSubscriptionId, LocalDateTime pauseEndAt) {
        if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank() || pauseEndAt == null) {
            return;
        }
        if (stripeSecretKey == null || stripeSecretKey.isBlank() || stripeSecretKey.contains("YOUR_")) {
            log.debug("Stripe not configured — skip pause sync");
            return;
        }
        Stripe.apiKey = stripeSecretKey;
        long resumesAt = pauseEndAt.toEpochSecond(ZoneOffset.UTC);
        try {
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .setPauseCollection(
                            SubscriptionUpdateParams.PauseCollection.builder()
                                    .setBehavior(SubscriptionUpdateParams.PauseCollection.Behavior.VOID)
                                    .setResumesAt(resumesAt)
                                    .build())
                    .build();
            Subscription sub = Subscription.retrieve(stripeSubscriptionId);
            sub.update(params);
            log.info("Stripe subscription {} pause_collection until epoch {}", stripeSubscriptionId, resumesAt);
        } catch (StripeException e) {
            log.warn("Stripe pause failed for {}: {} — DB pause still applied", stripeSubscriptionId, e.getMessage());
        }
    }

    public void resumeOnStripeIfPresent(String stripeSubscriptionId) {
        if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) {
            return;
        }
        if (stripeSecretKey == null || stripeSecretKey.isBlank() || stripeSecretKey.contains("YOUR_")) {
            return;
        }
        Stripe.apiKey = stripeSecretKey;
        try {
            Subscription sub = Subscription.retrieve(stripeSubscriptionId);
            Map<String, Object> params = new HashMap<>();
            params.put("pause_collection", null);
            sub.update(params);
            log.info("Stripe subscription {} pause_collection cleared", stripeSubscriptionId);
        } catch (StripeException e) {
            log.warn("Stripe resume failed for {}: {}", stripeSubscriptionId, e.getMessage());
        }
    }
}
