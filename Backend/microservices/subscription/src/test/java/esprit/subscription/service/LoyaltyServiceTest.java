package esprit.subscription.service;

import esprit.subscription.entity.LoyaltyTier;
import esprit.subscription.entity.UserLoyaltyAccount;
import esprit.subscription.repository.UserLoyaltyAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoyaltyServiceTest {

    @Mock UserLoyaltyAccountRepository repo;
    @InjectMocks LoyaltyService loyaltyService;

    @Test
    void applySuccessfulRenewal_reachesBronzeAt3Months() {
        UserLoyaltyAccount a = new UserLoyaltyAccount();
        a.setUserId(7L);
        a.setLoyaltyMonths(2);
        a.setWalletPoints(200);
        a.setLoyaltyTier(LoyaltyTier.NONE);
        a.setLastMilestoneMonth(0);

        when(repo.findByUserId(7L)).thenReturn(Optional.of(a));
        when(repo.save(any(UserLoyaltyAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        LoyaltyService.LoyaltyRewardResult out = loyaltyService.applySuccessfulRenewal(7L, 0);

        assertThat(out.account().getLoyaltyMonths()).isEqualTo(3);
        assertThat(out.account().getWalletPoints()).isEqualTo(300);
        assertThat(out.account().getLoyaltyTier()).isEqualTo(LoyaltyTier.BRONZE);
        assertThat(out.milestoneReached()).isTrue();
        assertThat(out.milestoneMonth()).isEqualTo(3);
    }

    @Test
    void previewRedeemPercent_requiresEnoughPoints() {
        UserLoyaltyAccount a = new UserLoyaltyAccount();
        a.setUserId(9L);
        a.setWalletPoints(400);
        a.setLoyaltyMonths(1);
        a.setLoyaltyTier(LoyaltyTier.NONE);
        a.setLastMilestoneMonth(0);
        when(repo.findByUserId(9L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> loyaltyService.previewRedeemPercent(9L, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Need at least 500 points");
    }
}

