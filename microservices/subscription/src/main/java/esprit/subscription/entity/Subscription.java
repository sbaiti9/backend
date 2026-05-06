package esprit.subscription.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties({"pricingPlan"})
public class Subscription implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private String userRole; // STUDENT/TRAINER/ENTERPRISE

    @Column(nullable = false)
    private String billingCycle; // MONTHLY/YEARLY

    /** ACTIVE / PAUSED / CANCELLED / EXPIRED */
    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime startDate;

    private LocalDateTime endDate;

    /** When set, Stripe Billing subscription id — used for pause/resume via Stripe API (optional for legacy checkout). */
    @Column(length = 64)
    private String stripeSubscriptionId;

    /** While status is PAUSED: access suspended; scheduler sets ACTIVE when this moment passes. */
    private LocalDateTime pauseEndAt;

    /** When the pause was started (audit). */
    private LocalDateTime pausedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private boolean autoRenew = false;

    /** Fin de période de grâce après échec de paiement (nullable). */
    private LocalDateTime gracePeriodEnd;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id", nullable = false)
    private PricingPlan pricingPlan;

    /** Expose plan id directly so the Angular frontend can use sub.planId */
    @JsonProperty("planId")
    public Long getPlanId() {
        return pricingPlan != null ? pricingPlan.getId() : null;
    }

    /** Expose plan name directly for convenience */
    @JsonProperty("planName")
    public String getPlanDisplayName() {
        return pricingPlan != null ? pricingPlan.getName() : null;
    }
}
