package esprit.subscription.service;

import esprit.subscription.entity.PricingPlan;
import esprit.subscription.entity.Subscription;
import esprit.subscription.repository.UserLoyaltyAccountRepository;
import esprit.subscription.repository.PaymentRecordRepository;
import esprit.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock esprit.subscription.client.EntrepriseClient entrepriseClient;
    @Mock PricingPlanService pricingPlanService;
    @Mock PaymentRecordRepository paymentRecordRepository;
    @Mock EmailService emailService;
    @Mock PlanAdvisorAiService planAdvisorAiService;
    @Mock StripeSubscriptionSyncService stripeSubscriptionSyncService;
    @Mock LoyaltyService loyaltyService;
    @Mock UserLoyaltyAccountRepository loyaltyAccountRepository;
    @Mock esprit.subscription.client.UserClient userClient;

    @InjectMocks SubscriptionService svc;

    @BeforeEach
    void setUp() {}

    @Test
    void subscribeCancelsExistingActiveAndCreatesNewActive() {
        Subscription old = new Subscription();
        old.setId(1L);
        old.setUserId(7L);
        old.setStatus("ACTIVE");

        when(subscriptionRepository.findByUserId(7L)).thenReturn(List.of(old));

        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        PricingPlan plan = new PricingPlan();
        plan.setId(10L);
        plan.setName("Pro");

        Subscription neu = new Subscription();
        neu.setUserId(7L);
        neu.setPricingPlan(plan);
        neu.setBillingCycle("MONTHLY");
        neu.setUserRole("ENTERPRISE");

        Subscription saved = svc.subscribe(neu);

        assertThat(old.getStatus()).isEqualTo("CANCELLED");
        assertThat(old.getEndDate()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getStartDate()).isNotNull();
        assertThat(saved.getEndDate()).isAfter(saved.getStartDate());

        verify(subscriptionRepository).save(old);
        verify(subscriptionRepository).save(neu);
    }

    @Test
    void checkSubscriptionStatusMarksExpiredWhenPastEndDate() {
        Subscription s = new Subscription();
        s.setId(1L);
        s.setUserId(9L);
        s.setStatus("ACTIVE");
        s.setEndDate(LocalDateTime.now().minusDays(1));

        when(subscriptionRepository.findTopByUserIdAndStatusInOrderByStartDateDesc(
                9L, List.of("ACTIVE", "PAUSED", "EXPIRED")))
                .thenReturn(Optional.of(s));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        String status = svc.checkSubscriptionStatus(9L);
        assertThat(status).isEqualTo("EXPIRED");
        assertThat(s.getStatus()).isEqualTo("EXPIRED");
        verify(subscriptionRepository).save(s);
    }

    @Test
    void pauseSubscription_setsPausedExtendsEndDateAndCallsStripeSync() {
        PricingPlan plan = new PricingPlan();
        plan.setId(1L);
        LocalDateTime end = LocalDateTime.now().plusMonths(1);
        Subscription sub = new Subscription();
        sub.setId(5L);
        sub.setUserId(99L);
        sub.setStatus("ACTIVE");
        sub.setEndDate(end);
        sub.setPricingPlan(plan);
        sub.setBillingCycle("MONTHLY");

        when(subscriptionRepository.findById(5L)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        Subscription out = svc.pauseSubscription(5L, 99L, 14);

        assertThat(out.getStatus()).isEqualToIgnoringCase("PAUSED");
        assertThat(out.getPausedAt()).isNotNull();
        assertThat(out.getPauseEndAt()).isNotNull();
        // Pause does NOT shift endDate immediately; it is shifted on resume based on actual pause duration.
        assertThat(out.getEndDate()).isEqualTo(end);
        verify(stripeSubscriptionSyncService).pauseOnStripeIfPresent(isNull(), any(LocalDateTime.class));
    }

    @Test
    void resumeSubscriptionEarly_setsActiveAndClearsPause() {
        Subscription sub = new Subscription();
        sub.setId(5L);
        sub.setUserId(99L);
        sub.setStatus("PAUSED");
        sub.setPauseEndAt(LocalDateTime.now().plusDays(3));
        when(subscriptionRepository.findById(5L)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        Subscription out = svc.resumeSubscriptionEarly(5L, 99L);

        assertThat(out.getStatus()).isEqualToIgnoringCase("ACTIVE");
        assertThat(out.getPauseEndAt()).isNull();
        assertThat(out.getPausedAt()).isNull();
        verify(stripeSubscriptionSyncService).resumeOnStripeIfPresent(null);
    }

    @Test
    void processPausedSubscriptionsResumeDue_marksActive() {
        Subscription sub = new Subscription();
        sub.setId(8L);
        sub.setUserId(1L);
        sub.setStatus("PAUSED");
        sub.setPauseEndAt(LocalDateTime.now().minusMinutes(2));
        when(subscriptionRepository.findPausedDueToResume(any(LocalDateTime.class))).thenReturn(List.of(sub));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        svc.processPausedSubscriptionsResumeDue();

        assertThat(sub.getStatus()).isEqualToIgnoringCase("ACTIVE");
        verify(stripeSubscriptionSyncService, atLeastOnce()).resumeOnStripeIfPresent(null);
    }
}

