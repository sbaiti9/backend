package esprit.subscription.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "liveness_verification", indexes = {
        @Index(name = "idx_lv_user_created", columnList = "userId,createdAt")
})
@Getter
@Setter
@NoArgsConstructor
public class LivenessVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ChallengeGesture challengeGesture;

    @Column(nullable = false, unique = true, length = 48)
    private String challengeToken;

    private Double livenessScore;
    private Double gestureScore;
    private Double antiSpoofScore;
    private Double globalScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LivenessVerificationStatus status = LivenessVerificationStatus.PENDING;

    @Column(nullable = false)
    private int attemptCount = 0;

    private String ipAddress;

    @Column(length = 512)
    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /** When set, the JWT bound to this challenge was already used for a payment (single-use). */
    private LocalDateTime jwtConsumedAt;

    @PrePersist
    void prePersist() {
        if (challengeToken == null || challengeToken.isBlank()) {
            challengeToken = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (expiresAt == null) {
            expiresAt = createdAt.plusMinutes(3);
        }
    }
}
