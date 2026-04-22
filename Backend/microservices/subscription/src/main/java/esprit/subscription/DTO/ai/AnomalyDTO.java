package esprit.subscription.DTO.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyDTO {
    private String metric;
    private String metricLabel;
    private double todayValue;
    private double mean;
    private double stddev;
    private double zScore;
    private boolean anomaly;
    /** NORMAL / WARNING / CRITICAL */
    private String severity;
}
