package esprit.subscription.DTO.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonteCarloForecastDTO {
    private double p10;
    private double p50;
    private double p90;
    private double currentMRR;
    private double minSim;
    private double maxSim;
    private int simulations;
}
