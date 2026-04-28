package esprit.subscription.service;

import esprit.subscription.DTO.UserProfileDto;
import esprit.subscription.DTO.ai.TutorSalaryRecommendationDto;
import esprit.subscription.client.UserClient;
import esprit.subscription.entity.Subscription;
import esprit.subscription.repository.PaymentRecordRepository;
import esprit.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TutorSalaryRecommendationServiceTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock PaymentRecordRepository paymentRecordRepository;
    @Mock UserClient userClient;
    @Mock GroqChatClient groqChatClient;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private TutorSalaryRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new TutorSalaryRecommendationService(
                subscriptionRepository,
                paymentRecordRepository,
                userClient,
                groqChatClient,
                objectMapper
        );
        ReflectionTestUtils.setField(service, "baseMonthly", 1200d);
        ReflectionTestUtils.setField(service, "minMonthly", 700d);
        ReflectionTestUtils.setField(service, "maxMonthly", 6000d);
        ReflectionTestUtils.setField(service, "weightPerformance", 0.50d);
        ReflectionTestUtils.setField(service, "weightReliability", 0.30d);
        ReflectionTestUtils.setField(service, "weightWorkload", 0.20d);
        ReflectionTestUtils.setField(service, "groqTemperature", 0.25d);
    }

    @Test
    void recommendFallsBackToDeterministicWhenGroqUnavailable() {
        UserProfileDto tutor = new UserProfileDto(7L, "Tutor", "One", "t@x.com", "TUTEUR", null, null, null, 4.6);
        when(userClient.getUserById(7L)).thenReturn(tutor);

        Subscription s = new Subscription();
        s.setStartDate(LocalDateTime.now().minusMonths(10));
        s.setUserRole("TUTEUR");
        when(subscriptionRepository.findByUserIdOrderByCreatedAtAsc(7L)).thenReturn(List.of(s));

        when(paymentRecordRepository.countSuccessfulByUserId(7L)).thenReturn(12L);
        when(paymentRecordRepository.countFailedByUserId(7L)).thenReturn(1L);
        when(paymentRecordRepository.sumSuccessfulAmountByUserId(7L)).thenReturn(4200d);
        when(groqChatClient.completeJson(anyString(), anyString(), anyDouble())).thenReturn(Optional.empty());

        TutorSalaryRecommendationDto out = service.recommend(7L, null);

        assertThat(out.isAiPowered()).isFalse();
        assertThat(out.getRole()).isEqualTo("TUTEUR");
        assertThat(out.getRecommendedMonthlySalary()).isBetween(700d, 6000d);
        assertThat(out.getSummary()).contains("Recommandation calculée");
        assertThat(out.getConfidence()).isBetween(50, 98);
    }

    @Test
    void recommendUsesGroqExplanationWhenJsonIsValid() {
        UserProfileDto tutor = new UserProfileDto(8L, "Tutor", "Two", "t2@x.com", "TUTEUR", null, null, null, 4.2);
        when(userClient.getUserById(8L)).thenReturn(tutor);
        Subscription s = new Subscription();
        s.setStartDate(LocalDateTime.now().minusMonths(3));
        s.setUserRole("TUTEUR");
        when(subscriptionRepository.findByUserIdOrderByCreatedAtAsc(8L)).thenReturn(List.of(s));
        when(paymentRecordRepository.countSuccessfulByUserId(8L)).thenReturn(4L);
        when(paymentRecordRepository.countFailedByUserId(8L)).thenReturn(0L);
        when(paymentRecordRepository.sumSuccessfulAmountByUserId(8L)).thenReturn(1900d);

        String ai = """
                {
                  "summary":"Profil fiable avec bonne note, recommandation alignée sur les indicateurs.",
                  "confidence":91,
                  "topFactors":["note élevée","fiabilité des paiements"],
                  "negotiationTips":["révision trimestrielle","bonus qualité"]
                }
                """;
        when(groqChatClient.completeJson(anyString(), anyString(), anyDouble())).thenReturn(Optional.of(ai));

        TutorSalaryRecommendationDto out = service.recommend(8L, 2500d);

        assertThat(out.isAiPowered()).isTrue();
        assertThat(out.getConfidence()).isEqualTo(91);
        assertThat(out.getTopFactors()).contains("note élevée");
        assertThat(out.getNegotiationTips()).contains("bonus qualité");
        assertThat(out.getSummary()).contains("Profil fiable");
    }
}
