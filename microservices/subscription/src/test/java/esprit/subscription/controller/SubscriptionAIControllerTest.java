package esprit.subscription.controller;

import esprit.subscription.DTO.ai.TutorSalaryRecommendationDto;
import esprit.subscription.service.PromoCodeService;
import esprit.subscription.service.SubscriptionAIService;
import esprit.subscription.service.TutorSalaryRecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SubscriptionAIControllerTest {

    @Mock SubscriptionAIService subscriptionAIService;
    @Mock PromoCodeService promoCodeService;
    @Mock TutorSalaryRecommendationService tutorSalaryRecommendationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SubscriptionAIController controller = new SubscriptionAIController(
                subscriptionAIService,
                promoCodeService,
                tutorSalaryRecommendationService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void tutorSalaryRecommendationReturnsPayload() throws Exception {
        TutorSalaryRecommendationDto dto = TutorSalaryRecommendationDto.builder()
                .userId(17L)
                .role("TUTEUR")
                .rating(4.5)
                .recommendedMonthlySalary(2100.0)
                .minMonthlySalary(1890.0)
                .maxMonthlySalary(2310.0)
                .currency("TND")
                .confidence(88)
                .topFactors(List.of("note élevée"))
                .summary("Résumé")
                .negotiationTips(List.of("Réviser tous les 3 mois"))
                .generatedAt(LocalDateTime.now())
                .modelSource("deterministic-v1")
                .build();
        when(tutorSalaryRecommendationService.recommend(17L, 3000.0)).thenReturn(dto);

        mockMvc.perform(get("/sub/ai/tutor-salary-recommendation")
                        .param("userId", "17")
                        .param("targetMonthlyRevenue", "3000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(17))
                .andExpect(jsonPath("$.recommendedMonthlySalary").value(2100.0))
                .andExpect(jsonPath("$.currency").value("TND"));
    }

    @Test
    void tutorSalaryRecommendationReturnsBadRequestOnInvalidRole() throws Exception {
        when(tutorSalaryRecommendationService.recommend(5L, null))
                .thenThrow(new IllegalArgumentException("User is not a tutor"));

        mockMvc.perform(get("/sub/ai/tutor-salary-recommendation")
                        .param("userId", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User is not a tutor"));
    }
}
