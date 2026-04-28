package esprit.subscription.DTO.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LTVUserDTO {
    private Long userId;
    private String plan;
    private double planPrice;
    private int churnScore;
    private double expectedMonths;
    private double ltv;
}
