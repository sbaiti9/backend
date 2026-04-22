package esprit.subscription.DTO.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpgradeCandidateDTO {
    private Long userId;
    private String plan;
    private int tenureMonths;
    private boolean autoRenew;
    private int upgradeScore;
    private String recommendedPitch;
}
