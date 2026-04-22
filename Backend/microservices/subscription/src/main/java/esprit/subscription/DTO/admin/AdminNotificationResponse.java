package esprit.subscription.DTO.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class AdminNotificationResponse {
    Long id;
    String type;
    Long sessionId;
    String userId;
    Long refUserBlockId;
    /** Per-notification secret for deep links; always serialized as {@code actionToken} for the Angular client. */
    @JsonProperty("actionToken")
    String actionToken;
    String message;
    String aiDecision;
    Integer aiConfidence;
    @JsonProperty("read")
    boolean read;
    LocalDateTime createdAt;
}
