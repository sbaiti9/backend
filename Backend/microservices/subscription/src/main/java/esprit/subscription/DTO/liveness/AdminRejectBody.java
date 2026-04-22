package esprit.subscription.DTO.liveness;

import lombok.Data;

@Data
public class AdminRejectBody {
    private String adminUsername;
    private String reason;
}
