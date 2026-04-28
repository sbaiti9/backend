package esprit.subscription.DTO.unlockchat;

import esprit.subscription.entity.MessageSender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnlockChatMessageDTO {
    private Long id;
    /** Present for SSE routing on the admin side */
    private Long sessionId;
    private MessageSender sender;
    private String content;
    private LocalDateTime sentAt;
}
