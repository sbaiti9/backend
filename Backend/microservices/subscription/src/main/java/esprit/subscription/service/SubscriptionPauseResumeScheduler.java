package esprit.subscription.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Auto-resume PAUSED subscriptions when {@link esprit.subscription.entity.Subscription#getPauseEndAt()} is reached.
 */
@Component
public class SubscriptionPauseResumeScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionPauseResumeScheduler.class);

    private final SubscriptionService subscriptionService;

    public SubscriptionPauseResumeScheduler(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /** Every 15 minutes — aligns with “resume on end date” without heavy load. */
    @Scheduled(cron = "0 */15 * * * ?")
    public void resumePausedSubscriptions() {
        try {
            subscriptionService.processPausedSubscriptionsResumeDue();
        } catch (Exception e) {
            log.error("Pause resume batch failed", e);
        }
    }
}
