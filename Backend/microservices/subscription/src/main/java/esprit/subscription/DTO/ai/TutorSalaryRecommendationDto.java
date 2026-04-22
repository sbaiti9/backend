package esprit.subscription.DTO.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TutorSalaryRecommendationDto {
    private Long userId;
    private String role;
    private Double rating;

    private Double targetMonthlyRevenue;
    private Double observedMonthlyRevenue;

    private int monthsActive;
    private int successfulPayments;
    private int failedPayments;

    private double performanceScore;
    private double reliabilityScore;
    private double workloadScore;
    private double overallScore;

    private double recommendedMonthlySalary;
    private double minMonthlySalary;
    private double maxMonthlySalary;
    private String currency;

    private int confidence;
    private List<String> topFactors;

    private boolean aiPowered;
    private String summary;
    private List<String> negotiationTips;

    private LocalDateTime generatedAt;
    private String modelSource;
}
