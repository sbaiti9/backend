package esprit.subscription.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanRecommendationDto {
    private Long planId;
    private String planName;
    private String reason;
    private int activeUserCount;
    private double adoptionRate;
    private boolean mostPopular;
    private boolean bestValue;
    /** True when produced by Groq plan advisor (user profile + catalog). */
    @Builder.Default
    private boolean aiPowered = false;
    /** 0–100 when AI-powered; null otherwise. */
    private Integer confidenceScore;
    /** Suggested billing cycle from AI: MONTHLY, YEARLY, or null. */
    private String billingCycleHint;
}
