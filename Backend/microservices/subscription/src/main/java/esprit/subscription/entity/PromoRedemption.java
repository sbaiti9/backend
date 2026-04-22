package esprit.subscription.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "promo_redemptions", indexes = {
        @Index(name = "idx_promo_user", columnList = "promo_code_id,user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromoRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "promo_code_id", nullable = false)
    private PromoCode promoCode;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime redeemedAt;

    private Long subscriptionId;

    @PrePersist
    void prePersist() {
        if (redeemedAt == null) {
            redeemedAt = LocalDateTime.now();
        }
    }
}
