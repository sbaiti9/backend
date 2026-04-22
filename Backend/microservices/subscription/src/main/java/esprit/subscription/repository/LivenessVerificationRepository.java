package esprit.subscription.repository;

import esprit.subscription.entity.LivenessVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface LivenessVerificationRepository extends JpaRepository<LivenessVerification, Long> {

    Optional<LivenessVerification> findByChallengeToken(String challengeToken);

    int countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);
}
