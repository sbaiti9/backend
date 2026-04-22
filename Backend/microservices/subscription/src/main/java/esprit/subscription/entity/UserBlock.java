package esprit.subscription.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_blocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    private String userId;

    @Column(name = "block_reason", nullable = false, columnDefinition = "TEXT")
    private String blockReason;

    @Column(name = "blocked_at", nullable = false)
    private LocalDateTime blockedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private BlockStatus status;

    @Column(name = "unlocked_at")
    private LocalDateTime unlockedAt;

    @Column(name = "unlocked_by", length = 255)
    private String unlockedBy;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "last_session_payload", columnDefinition = "TEXT")
    private String lastSessionPayload;

    @Column(name = "re_verification_passed_at")
    private LocalDateTime reVerificationPassedAt;
}
