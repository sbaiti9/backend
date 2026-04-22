package esprit.subscription.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class subb {
    private long userId;
    private String userRole;
    private Long planId;
    private String billingCycle;
    private String promoCode; // optional — applied to the Stripe amount if provided
    /** JWT from liveness verify — optional if sent as X-Liveness-Token header instead */
    private String livenessToken;
    /** Redeem wallet points (500 points = 5%% discount). */
    private Boolean redeemPoints;
}
