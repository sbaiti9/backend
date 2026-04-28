package esprit.subscription.repository;

import esprit.subscription.entity.UserLoyaltyAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserLoyaltyAccountRepository extends JpaRepository<UserLoyaltyAccount, Long> {
    Optional<UserLoyaltyAccount> findByUserId(Long userId);
}

