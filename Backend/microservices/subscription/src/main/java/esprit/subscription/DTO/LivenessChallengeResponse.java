package esprit.subscription.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LivenessChallengeResponse {
    private String challengeToken;
    private String gesture;
    private String gestureLabel;
    /** ISO-8601 */
    private String expiresAt;
}
