package esprit.subscription.DTO.liveness;

import lombok.Data;

@Data
public class ClientBlockRegisterRequest {
    private String userId;
    private String reason;
    private String sessionPayload;
}
