package esprit.subscription.DTO.unlockchat;

import esprit.subscription.entity.AiDecision;
import esprit.subscription.entity.ChatSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnlockChatSessionDTO {
    private Long sessionId;
    private String userId;
    private Long blockId;
    private ChatSessionStatus status;
    private String blockReason;
    private Integer failureCount;
    private LocalDateTime blockedAt;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
    private String closedBy;
    private List<UnlockChatMessageDTO> messages;

    private AiDecision aiDecision;
    private Integer aiConfidence;
    private String aiReasoning;
    private Boolean aiRecommendUnblock;
    private LocalDateTime aiAnalyzedAt;
}
