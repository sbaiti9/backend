package esprit.subscription.DTO.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactWindowDto {
    private Long userId;
    private int contactInDays;
    private String contactDateLabel;
    private String channel;
    private String channelLabel;
    private int avgResponseWindow;
    private boolean isOverdue;
    /** URGENT / SOON / PLANNED */
    private String urgencyLevel;
}
