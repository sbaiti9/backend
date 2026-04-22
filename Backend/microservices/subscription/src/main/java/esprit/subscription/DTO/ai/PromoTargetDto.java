package esprit.subscription.DTO.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoTargetDto {
    private Long userId;
    private String planName;
    private int daysUntilExpiry;
    private int churnScore;
    private int recommendedDiscount;
    private int priorityScore;
    private String reason;
}
