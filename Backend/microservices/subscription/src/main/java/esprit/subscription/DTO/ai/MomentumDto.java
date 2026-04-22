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
public class MomentumDto {
    private int netGrowthThisWeek;
    private int netGrowthLastWeek;
    private String globalTrend;
    private String globalTrendLabel;
    private String alertMessage;
    @Builder.Default
    private List<WeeklySnapshotDto> weeklyHistory = new ArrayList<>();
    @Builder.Default
    private List<PlanMomentumDto> perPlan = new ArrayList<>();
}
