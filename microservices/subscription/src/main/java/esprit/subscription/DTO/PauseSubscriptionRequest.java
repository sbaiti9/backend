package esprit.subscription.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PauseSubscriptionRequest {
    /** Allowed: 7, 14, 30 */
    private int pauseDays;
}
