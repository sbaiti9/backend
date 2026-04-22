package esprit.subscription.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Long subscriptionId;

    /** e.g. RENEWAL */
    @Column(nullable = false, length = 32)
    private String type;

    private Double amount;

    /** SUCCESS, FAILED, PENDING — nullable pour les anciennes lignes. */
    @Column(length = 32)
    private String status;

    private LocalDateTime paidAt;

    @Column(length = 64)
    private String planName;

    /** Stripe session id / external payment id for idempotency. */
    @Column(length = 128)
    private String externalPaymentRef;

    @Column(length = 16)
    private String billingCycle;

    private LocalDateTime endDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
