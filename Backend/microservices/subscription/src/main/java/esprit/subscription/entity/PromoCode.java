package esprit.subscription.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "promo_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    /** When {@link #discountType} is null or {@link PromoDiscountType#PERCENT}. */
    @Column(nullable = false)
    private Double discountPercent;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PromoDiscountType discountType;

    /** Used when {@link #discountType} is {@link PromoDiscountType#FIXED_AMOUNT} (EUR, same unit as plan prices). */
    private Double discountAmount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PromoAudience applicableAudience = PromoAudience.ALL;

    /**
     * Comma-separated plan ids, e.g. {@code 1,3}. Null or blank = all plans.
     */
    @Column(length = 500)
    private String allowedPlanIds;

    /**
     * Max times a single user can redeem this code. Null = no per-user cap (legacy behaviour; still limited by {@link #maxUses}).
     */
    private Integer maxRedemptionsPerUser;

    @Column(nullable = false)
    private Long createdByUserId;

    private Integer maxUses;

    private Integer currentUses = 0;

    private LocalDateTime expirationDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private boolean active = true;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (currentUses == null) {
            currentUses = 0;
        }
        if (discountType == null) {
            discountType = PromoDiscountType.PERCENT;
        }
        if (applicableAudience == null) {
            applicableAudience = PromoAudience.ALL;
        }
    }

    /** Effective type for validation (existing rows may have null in DB before migration). */
    public PromoDiscountType effectiveDiscountType() {
        return discountType != null ? discountType : PromoDiscountType.PERCENT;
    }
}
