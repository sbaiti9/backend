package esprit.subscription.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "blocked_payment_attempt", indexes = {
        @Index(name = "idx_bpa_user_until", columnList = "userId,blockedUntil")
})
@Getter
@Setter
@NoArgsConstructor
public class BlockedPaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private String ipAddress;

    @Column(nullable = false)
    private LocalDateTime blockedUntil;

    @Column(length = 255)
    private String reason;
}
