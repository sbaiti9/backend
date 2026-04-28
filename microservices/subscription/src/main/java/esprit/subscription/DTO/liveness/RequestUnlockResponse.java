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
public class RequestUnlockResponse {
    private String status;
    private String message;
    private Long blockId;
    private BlockStatus blockStatus;
}
