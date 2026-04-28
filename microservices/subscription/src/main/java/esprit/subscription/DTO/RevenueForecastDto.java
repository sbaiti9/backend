package esprit.subscription.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Réponse alignée sur le modèle Angular {@code RevenueForecast} (prévisions revenus admin).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueForecastDto {
    private double currentMrr;
    private double monthlyRevenue;
    private double yearlyRevenue;
    private double projected3Month;
    private double projected6Month;
    private double projectedArr;
    /** JSON : [{ "month": "yyyy-MM", "mrr": number }] */
    private String historyJson;
    /** JSON : [{ "month": "yyyy-MM", "mrr": number, "lower": number, "upper": number }] */
    private String forecastJson;
    private String generatedAt;
}
