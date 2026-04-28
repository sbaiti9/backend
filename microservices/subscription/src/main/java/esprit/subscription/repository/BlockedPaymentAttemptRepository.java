package esprit.subscription.repository;

import esprit.subscription.entity.BlockedPaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface BlockedPaymentAttemptRepository extends JpaRepository<BlockedPaymentAttempt, Long> {

    Optional<BlockedPaymentAttempt> findFirstByUserIdAndBlockedUntilAfterOrderByBlockedUntilDesc(
            Long userId, LocalDateTime now);
}
