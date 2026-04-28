package esprit.subscription.DTO.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterventionDto {
    private Long userId;
    private String action;
    private String label;
    private int confidence;
    private String detail;
    private String dnaArchetype;
    private int churnScore;
}
