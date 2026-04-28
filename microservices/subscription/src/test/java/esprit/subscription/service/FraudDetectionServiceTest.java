package esprit.subscription.service;

import esprit.subscription.entity.FraudAlert;
import esprit.subscription.entity.PricingPlan;
import esprit.subscription.entity.Subscription;
import esprit.subscription.repository.FraudAlertRepository;
import esprit.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock FraudAlertRepository fraudAlertRepository;
    @Mock SubscriptionRepository subscriptionRepository;

    @InjectMocks FraudDetectionService svc;

    @BeforeEach
    void setUp() {}

    @Test
    void analyseSubscriptionCreatesRapidChangeAlert_whenMoreThan3RecentSubscriptions() {
        Subscription current = new Subscription();
        current.setUserId(1L);
        current.setStatus("ACTIVE");
        current.setBillingCycle("MONTHLY");
        current.setUserRole("ENTERPRISE");
        PricingPlan plan = new PricingPlan();
        plan.setName("Pro");
        current.setPricingPlan(plan);

        Subscription h1 = new Subscription(); h1.setCreatedAt(LocalDateTime.now().minusDays(2)); h1.setStatus("ACTIVE");
        Subscription h2 = new Subscription(); h2.setCreatedAt(LocalDateTime.now().minusDays(3)); h2.setStatus("ACTIVE");
        Subscription h3 = new Subscription(); h3.setCreatedAt(LocalDateTime.now().minusDays(4)); h3.setStatus("ACTIVE");
        Subscription h4 = new Subscription(); h4.setCreatedAt(LocalDateTime.now().minusDays(5)); h4.setStatus("ACTIVE");

        when(subscriptionRepository.findByUserId(1L)).thenReturn(List.of(h1, h2, h3, h4));
        when(fraudAlertRepository.save(any(FraudAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        svc.analyseSubscription(current);

        ArgumentCaptor<FraudAlert> cap = ArgumentCaptor.forClass(FraudAlert.class);
        verify(fraudAlertRepository).save(cap.capture());
        FraudAlert alert = cap.getValue();
        assertThat(alert.getAlertType()).isEqualTo("RAPID_CHANGE");
        assertThat(alert.getRiskLevel()).isEqualTo("HIGH");
        assertThat(alert.getUserId()).isEqualTo(1L);
    }

    @Test
    void analyseSubscriptionCreatesTrialAbuseAlert_whenAtLeast2CancelledInLast7Days() {
        Subscription current = new Subscription();
        current.setUserId(2L);
        current.setStatus("ACTIVE");
        current.setBillingCycle("MONTHLY");
        current.setUserRole("ENTERPRISE");

        Subscription c1 = new Subscription(); c1.setStatus("CANCELLED"); c1.setEndDate(LocalDateTime.now().minusDays(1));
        Subscription c2 = new Subscription(); c2.setStatus("CANCELLED"); c2.setEndDate(LocalDateTime.now().minusDays(2));

        when(subscriptionRepository.findByUserId(2L)).thenReturn(List.of(c1, c2));
        when(fraudAlertRepository.save(any(FraudAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        svc.analyseSubscription(current);

        ArgumentCaptor<FraudAlert> cap = ArgumentCaptor.forClass(FraudAlert.class);
        verify(fraudAlertRepository).save(cap.capture());
        FraudAlert alert = cap.getValue();
        assertThat(alert.getAlertType()).isEqualTo("TRIAL_ABUSE");
        assertThat(alert.getRiskLevel()).isEqualTo("MEDIUM");
        assertThat(alert.getUserId()).isEqualTo(2L);
    }
}

