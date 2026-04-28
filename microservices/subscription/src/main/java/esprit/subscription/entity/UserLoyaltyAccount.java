package esprit.subscription.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_loyalty_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserLoyaltyAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private Integer loyaltyMonths = 0;

    @Column(nullable = false)
    private Integer walletPoints = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LoyaltyTier loyaltyTier = LoyaltyTier.NONE;

    /** Highest milestone already rewarded (0, 3, 6, 12). */
    @Column(nullable = false)
    private Integer lastMilestoneMonth = 0;

    /** Last generated Stripe promotion code for milestone reward. */
    @Column(length = 128)
    private String lastMilestonePromoCode;

    @Column(length = 64)
    private String lastMilestoneStripeCouponId;

    @Column(length = 64)
    private String lastMilestoneStripePromotionCodeId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (loyaltyMonths == null) {
            loyaltyMonths = 0;
        }
        if (walletPoints == null) {
            walletPoints = 0;
        }
        if (loyaltyTier == null) {
            loyaltyTier = LoyaltyTier.NONE;
        }
        if (lastMilestoneMonth == null) {
            lastMilestoneMonth = 0;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

