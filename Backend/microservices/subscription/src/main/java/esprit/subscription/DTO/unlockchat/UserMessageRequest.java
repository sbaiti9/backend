package esprit.subscription.DTO.unlockchat;

import lombok.Data;

@Data
public class UserMessageRequest {
    private Long sessionId;
    private String userId;
    private String content;
}
