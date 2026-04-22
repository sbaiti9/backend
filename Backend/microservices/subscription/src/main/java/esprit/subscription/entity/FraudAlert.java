package esprit.subscription.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "fraud_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String alertType; // MULTIPLE_PLANS, RAPID_CHANGE, PROMO_ABUSE, etc.

    @Column(nullable = false)
    private String riskLevel; // HIGH, MEDIUM, LOW

    @Column(length = 1000)
    private String details;

    @Column(nullable = false)
    private LocalDateTime detectedAt;

    private boolean resolved = false;

    // Snapshot fields populated at detection time
    private String userRole;           // role at alert time (STUDENT / TRAINER / ENTERPRISE)
    private String planName;           // snapshot of plan name at detection
    private String billingCycle;       // MONTHLY or YEARLY at detection
    private String subscriptionStatus; // subscription status at detection

    @PrePersist
    public void prePersist() {
        if (detectedAt == null) {
            detectedAt = LocalDateTime.now();
        }
    }
}
