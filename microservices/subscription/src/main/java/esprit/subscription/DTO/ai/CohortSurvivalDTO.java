package esprit.subscription.DTO.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CohortSurvivalDTO {
    private String cohortMonth;
    private int totalUsers;
    private double survival30;
    private double survival60;
    private double survival90;
}
