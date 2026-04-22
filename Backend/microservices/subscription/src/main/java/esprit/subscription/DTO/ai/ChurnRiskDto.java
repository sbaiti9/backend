package esprit.subscription.DTO.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChurnRiskDto {
    private Long userId;
    private int score;
    /** HIGH / MEDIUM / LOW */
    private String riskLevel;
    @Builder.Default
    private List<String> topReasons = new ArrayList<>();
    private String recommendation;
}
