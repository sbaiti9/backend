package esprit.subscription.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LivenessVerifyResponse {
    private boolean passed;
    private double globalScore;
    private String jwtToken;
    /** ISO-8601 when user is temporarily blocked after 3 failures */
    private String blockedUntil;
    /** Optional failure detail for the client */
    private String message;
}
