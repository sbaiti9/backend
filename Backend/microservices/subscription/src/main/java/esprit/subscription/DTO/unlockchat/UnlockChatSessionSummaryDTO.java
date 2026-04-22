package esprit.subscription.DTO.unlockchat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnlockChatSessionSummaryDTO {
    private Long sessionId;
    private String userId;
    private String userDisplayName;
    private String blockReason;
    private long messageCount;
    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;
    private long waitingMinutes;
}
