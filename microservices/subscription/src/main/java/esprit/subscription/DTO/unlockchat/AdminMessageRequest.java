package esprit.subscription.DTO.unlockchat;

import lombok.Data;

@Data
public class AdminMessageRequest {
    private Long sessionId;
    private String adminUsername;
    private String content;
}
