package esprit.subscription.DTO.unlockchat;

import lombok.Data;

@Data
public class UnlockChatStartRequest {
    private String userId;
    private Long blockId;
    private String message;
}
