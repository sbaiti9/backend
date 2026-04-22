package esprit.subscription.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "admin_notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String type;

    /** Unlock-chat session (null for alerts without a session, e.g. USER_BLOCKED before chat). */
    @Column(name = "session_id", nullable = true)
    private Long sessionId;

    /** Unlock-chat user id (string form, e.g. numeric id as text). */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** Optional link to {@code user_blocks.id} for USER_BLOCKED notifications. */
    @Column(name = "ref_user_block_id")
    private Long refUserBlockId;

    /** Per-notification secret for admin deep links; rotate via admin API. */
    @Column(name = "action_token", length = 64, unique = true)
    private String actionToken;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "ai_decision", length = 16)
    private String aiDecision;

    @Column(name = "ai_confidence")
    private Integer aiConfidence;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean isRead = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void ensureActionToken() {
        if (actionToken == null || actionToken.isBlank()) {
            actionToken = UUID.randomUUID().toString();
        }
    }
}
