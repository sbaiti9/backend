package esprit.subscription.DTO.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklySnapshotDto {
    private String weekLabel;
    private int newSubs;
    private int cancellations;
    private int netGrowth;
}
