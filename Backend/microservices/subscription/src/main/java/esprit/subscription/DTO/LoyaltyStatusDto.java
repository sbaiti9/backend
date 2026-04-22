package esprit.subscription.DTO;

import esprit.subscription.entity.LoyaltyTier;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyStatusDto {
    private Long userId;
    private Integer loyaltyMonths;
    private Integer walletPoints;
    private LoyaltyTier loyaltyTier;
    private Integer monthsToNextTier;
    private String nextTier;
    private Integer progressPercent;
    private Integer pointsNeededForRedemption;
    private String progressLabel;
    private String badgeLabel;
    private Integer lastMilestoneMonth;
    private String lastMilestonePromoCode;
}

