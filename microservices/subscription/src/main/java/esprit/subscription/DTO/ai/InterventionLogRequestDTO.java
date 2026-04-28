package esprit.subscription.DTO.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterventionLogRequestDTO {
    private Long userId;
    private String actionType;
    private String outcome;
    private LocalDateTime recommendedAt;
    private LocalDateTime executedAt;
    private Integer confidenceAtTime;
}
