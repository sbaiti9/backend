package esprit.subscription.service;

import esprit.subscription.DTO.PlanRecommendationDto;
import esprit.subscription.client.EntrepriseClient;
import esprit.subscription.client.UserClient;
import esprit.subscription.entity.PricingPlan;
import esprit.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanAdvisorAiServiceTest {

    @Mock GroqChatClient groqChatClient;
    @Mock UserClient userClient;
    @Mock EntrepriseClient entrepriseClient;
    @Mock PricingPlanService pricingPlanService;
    @Mock SubscriptionRepository subscriptionRepository;

    private PlanAdvisorAiService svc;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @BeforeEach
    void setUp() {
        svc = new PlanAdvisorAiService(
                groqChatClient,
                userClient,
                entrepriseClient,
                pricingPlanService,
                subscriptionRepository,
                objectMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "planAdvisorGroqTemperature", 0.35);
    }

    @Test
    void adviseParsesGroqJsonAndMarksAiPowered() {
        PricingPlan p = new PricingPlan();
        p.setId(2L);
        p.setName("PRO");
        p.setMonthlyPrice(29.0);
        p.setYearlyPrice(290.0);
        p.setHighlight(false);
        p.setIsActive(true);

        when(pricingPlanService.findById(2L)).thenReturn(Optional.of(p));
        when(pricingPlanService.findAllActive()).thenReturn(List.of(p));
        when(subscriptionRepository.findFirstByUserIdAndStatusIn(
                org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.countActiveByUserRoleNormalized("ETUDIANT")).thenReturn(10L);
        when(subscriptionRepository.countByStatus("ACTIVE")).thenReturn(100L);
        when(subscriptionRepository.countActiveByPlanForRole("ETUDIANT")).thenReturn(List.of());

        PlanRecommendationDto baseline = PlanRecommendationDto.builder()
                .planId(1L)
                .planName("BASIC")
                .reason("stats")
                .activeUserCount(3)
                .adoptionRate(30.0)
                .mostPopular(true)
                .bestValue(false)
                .build();

        String groqJson = """
                {"recommendedPlanId":2,"reason":"Adapté à votre profil étudiant.","confidence":88,"preferredBillingCycle":"YEARLY"}
                """;
        when(groqChatClient.completeJson(anyString(), anyString(), anyDouble()))
                .thenReturn(Optional.of(groqJson));

        PlanRecommendationDto out = svc.advise("ETUDIANT", 5L, baseline);

        assertThat(out.isAiPowered()).isTrue();
        assertThat(out.getPlanId()).isEqualTo(2L);
        assertThat(out.getPlanName()).isEqualTo("PRO");
        assertThat(out.getReason()).contains("profil");
        assertThat(out.getConfidenceScore()).isEqualTo(88);
        assertThat(out.getBillingCycleHint()).isEqualTo("YEARLY");
    }

    @Test
    void adviseFallsBackWhenGroqEmpty() {
        when(groqChatClient.completeJson(anyString(), anyString(), anyDouble()))
                .thenReturn(Optional.empty());

        PlanRecommendationDto baseline = PlanRecommendationDto.builder()
                .planId(1L)
                .planName("BASIC")
                .reason("fallback reason")
                .activeUserCount(1)
                .adoptionRate(50.0)
                .mostPopular(true)
                .bestValue(false)
                .build();

        PlanRecommendationDto out = svc.advise("ETUDIANT", 5L, baseline);

        assertThat(out.isAiPowered()).isFalse();
        assertThat(out.getReason()).isEqualTo("fallback reason");
    }
}
