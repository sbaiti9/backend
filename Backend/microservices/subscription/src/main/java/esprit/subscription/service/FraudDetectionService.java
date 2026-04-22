package esprit.subscription.service;

import esprit.subscription.entity.FraudAlert;
import esprit.subscription.entity.Subscription;
import esprit.subscription.repository.FraudAlertRepository;
import esprit.subscription.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);

    private final FraudAlertRepository fraudAlertRepository;
    private final SubscriptionRepository subscriptionRepository;

    public FraudDetectionService(FraudAlertRepository fraudAlertRepository,
                                  SubscriptionRepository subscriptionRepository) {
        this.fraudAlertRepository = fraudAlertRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * Analyse a subscription event for potential fraud and create alerts if needed.
     * Call this right after a new subscription is persisted.
     */
    @Transactional
    public void analyseSubscription(Subscription subscription) {
        List<Subscription> history = subscriptionRepository.findByUserId(subscription.getUserId());

        // Rule 1 — Rapid plan changes (more than 3 subscriptions in 30 days)
        long recentCount = history.stream()
                .filter(s -> s.getCreatedAt() != null
                        && s.getCreatedAt().isAfter(LocalDateTime.now().minusDays(30)))
                .count();

        if (recentCount > 3) {
            createAlert(subscription, "RAPID_CHANGE",
                    "HIGH",
                    "User created " + recentCount + " subscriptions in the last 30 days.");
        }

        // Rule 2 — Multiple CANCELLED subscriptions in the past week (trial abuse)
        long cancelledRecent = history.stream()
                .filter(s -> "CANCELLED".equals(s.getStatus())
                        && s.getEndDate() != null
                        && s.getEndDate().isAfter(LocalDateTime.now().minusDays(7)))
                .count();

        if (cancelledRecent >= 2) {
            createAlert(subscription, "TRIAL_ABUSE",
                    "MEDIUM",
                    "User cancelled " + cancelledRecent + " subscriptions in the last 7 days.");
        }
    }

    private void createAlert(Subscription subscription, String alertType, String riskLevel, String details) {
        FraudAlert alert = new FraudAlert();
        alert.setUserId(subscription.getUserId());
        alert.setAlertType(alertType);
        alert.setRiskLevel(riskLevel);
        alert.setDetails(details);
        alert.setResolved(false);

        // Populate the four snapshot fields
        alert.setUserRole(subscription.getUserRole());
        alert.setPlanName(subscription.getPricingPlan() != null
                ? subscription.getPricingPlan().getName() : null);
        alert.setBillingCycle(subscription.getBillingCycle());
        alert.setSubscriptionStatus(subscription.getStatus());

        fraudAlertRepository.save(alert);
        log.warn("FraudAlert created — userId={} type={} risk={}", alert.getUserId(), alertType, riskLevel);
    }

    public List<FraudAlert> findAll() {
        return fraudAlertRepository.findAll();
    }

    public List<FraudAlert> findByRiskLevel(String riskLevel) {
        return fraudAlertRepository.findByRiskLevel(riskLevel);
    }

    @Transactional
    public FraudAlert resolve(Long alertId) {
        return fraudAlertRepository.findById(alertId).map(alert -> {
            alert.setResolved(true);
            return fraudAlertRepository.save(alert);
        }).orElseThrow(() -> new RuntimeException("FraudAlert not found: " + alertId));
    }
}
