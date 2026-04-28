package esprit.subscription.DTO.liveness;

import lombok.Data;

@Data
public class VerifyTokenRequest {
    private String token;
    private String userId;
}
