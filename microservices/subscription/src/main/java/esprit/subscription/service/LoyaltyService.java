package esprit.subscription.service;

import esprit.subscription.DTO.LoyaltyStatusDto;
import esprit.subscription.entity.LoyaltyTier;
import esprit.subscription.entity.UserLoyaltyAccount;
import esprit.subscription.repository.UserLoyaltyAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class LoyaltyService {
    public static final int POINTS_PER_SUCCESSFUL_RENEWAL = 100;
    public static final int REDEEM_POINTS_COST = 500;
    public static final int REDEEM_DISCOUNT_PERCENT = 5;

    private final UserLoyaltyAccountRepository loyaltyRepo;

    public LoyaltyService(UserLoyaltyAccountRepository loyaltyRepo) {
        this.loyaltyRepo = loyaltyRepo;
    }

    public record LoyaltyRewardResult(
            UserLoyaltyAccount account,
            boolean milestoneReached,
            int milestoneMonth,
            int milestoneDiscountPercent
    ) {}

    @Transactional
    public UserLoyaltyAccount getOrCreate(Long userId) {
        return loyaltyRepo.findByUserId(userId).orElseGet(() -> {
            UserLoyaltyAccount a = new UserLoyaltyAccount();
            a.setUserId(userId);
            a.setLoyaltyMonths(0);
            a.setWalletPoints(0);
            a.setLoyaltyTier(LoyaltyTier.NONE);
            a.setLastMilestoneMonth(0);
            return loyaltyRepo.save(a);
        });
    }

    @Transactional
    public LoyaltyStatusDto getStatus(Long userId) {
        UserLoyaltyAccount a = getOrCreate(userId);
        int months = Optional.ofNullable(a.getLoyaltyMonths()).orElse(0);
        int points = Optional.ofNullable(a.getWalletPoints()).orElse(0);
        LoyaltyTier tier = Optional.ofNullable(a.getLoyaltyTier()).orElse(LoyaltyTier.NONE);
        LoyaltyTier computedTier = computeTier(months);

        // Self-heal legacy data: recalculate tier from months when stored tier is stale.
        if (tier != computedTier) {
            a.setLoyaltyTier(computedTier);
            a = loyaltyRepo.save(a);
            tier = computedTier;
        }

        int next = nextTierTarget(months);
        int monthsToNext = Math.max(0, next - months);
        String nextTier = nextTierName(months);
        int pct = progressPercent(months);
        int pointsNeeded = Math.max(0, REDEEM_POINTS_COST - points);

        LoyaltyStatusDto dto = new LoyaltyStatusDto();
        dto.setUserId(userId);
        dto.setLoyaltyMonths(months);
        dto.setWalletPoints(points);
        dto.setLoyaltyTier(tier);
        dto.setMonthsToNextTier(monthsToNext);
        dto.setNextTier(nextTier);
        dto.setProgressPercent(pct);
        dto.setPointsNeededForRedemption(pointsNeeded);
        dto.setProgressLabel(monthsToNext <= 0 ? "Gold tier reached" : monthsToNext + " more months to " + nextTier + " tier.");
        dto.setBadgeLabel(tier.name());
        dto.setLastMilestoneMonth(Optional.ofNullable(a.getLastMilestoneMonth()).orElse(0));
        dto.setLastMilestonePromoCode(a.getLastMilestonePromoCode());
        return dto;
    }

    @Transactional
    public LoyaltyRewardResult applySuccessfulRenewal(Long userId, int pointsToRedeem) {
        UserLoyaltyAccount a = getOrCreate(userId);
        int points = Optional.ofNullable(a.getWalletPoints()).orElse(0);
        int months = Optional.ofNullable(a.getLoyaltyMonths()).orElse(0);

        if (pointsToRedeem > 0) {
            if (pointsToRedeem != REDEEM_POINTS_COST) {
                throw new IllegalArgumentException("Points redemption must be exactly " + REDEEM_POINTS_COST);
            }
            if (points < pointsToRedeem) {
                throw new IllegalArgumentException("Insufficient wallet points");
            }
            points -= pointsToRedeem;
        }

        points += POINTS_PER_SUCCESSFUL_RENEWAL;
        months += 1;

        a.setWalletPoints(points);
        a.setLoyaltyMonths(months);
        a.setLoyaltyTier(computeTier(months));

        int milestone = currentMilestone(months);
        int lastMilestone = Optional.ofNullable(a.getLastMilestoneMonth()).orElse(0);
        boolean reached = milestone > 0 && milestone > lastMilestone;
        if (reached) {
            a.setLastMilestoneMonth(milestone);
        }

        UserLoyaltyAccount saved = loyaltyRepo.save(a);
        return new LoyaltyRewardResult(saved, reached, milestone, discountForMilestone(milestone));
    }

    @Transactional
    public int previewRedeemPercent(Long userId, boolean redeemPoints) {
        if (!redeemPoints) {
            return 0;
        }
        UserLoyaltyAccount a = getOrCreate(userId);
        int points = Optional.ofNullable(a.getWalletPoints()).orElse(0);
        if (points < REDEEM_POINTS_COST) {
            throw new IllegalArgumentException("Need at least " + REDEEM_POINTS_COST + " points to redeem");
        }
        return REDEEM_DISCOUNT_PERCENT;
    }

    private static LoyaltyTier computeTier(int months) {
        if (months >= 12) return LoyaltyTier.GOLD;
        if (months >= 6) return LoyaltyTier.SILVER;
        if (months >= 3) return LoyaltyTier.BRONZE;
        return LoyaltyTier.NONE;
    }

    private static int currentMilestone(int months) {
        if (months >= 12) return 12;
        if (months >= 6) return 6;
        if (months >= 3) return 3;
        return 0;
    }

    private static int discountForMilestone(int month) {
        return switch (month) {
            case 3 -> 5;
            case 6 -> 10;
            case 12 -> 15;
            default -> 0;
        };
    }

    private static int nextTierTarget(int months) {
        if (months < 3) return 3;
        if (months < 6) return 6;
        if (months < 12) return 12;
        return 12;
    }

    private static String nextTierName(int months) {
        if (months < 3) return "Bronze";
        if (months < 6) return "Silver";
        if (months < 12) return "Gold";
        return "Gold";
    }

    private static int progressPercent(int months) {
        if (months < 3) return Math.min(100, Math.round((months / 3f) * 100f));
        if (months < 6) return Math.min(100, Math.round(((months - 3) / 3f) * 100f));
        if (months < 12) return Math.min(100, Math.round(((months - 6) / 6f) * 100f));
        return 100;
    }
}

