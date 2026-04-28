package esprit.subscription.DTO.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionAccuracyDTO {
    private String actionType;
    private int total;
    private int successes;
    private double accuracy;
    /** UP / DOWN / STABLE */
    private String trend;
}
