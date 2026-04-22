package esprit.subscription.DTO.liveness;

import esprit.subscription.entity.BlockStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBlockStatusResponse {
    private boolean blocked;
    private BlockStatus status;
    private String reason;
    private Long blockId;
}
