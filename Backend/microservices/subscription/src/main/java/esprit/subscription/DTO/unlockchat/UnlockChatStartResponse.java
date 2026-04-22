package esprit.subscription.DTO.unlockchat;

import esprit.subscription.entity.ChatSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnlockChatStartResponse {
    private Long sessionId;
    private ChatSessionStatus status;
    private LocalDateTime createdAt;
}
