package esprit.subscription.repository;

import esprit.subscription.entity.BlockStatus;
import esprit.subscription.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    Optional<UserBlock> findByUserId(String userId);

    List<UserBlock> findByStatusOrderByBlockedAtDesc(BlockStatus status);

    List<UserBlock> findAllByOrderByBlockedAtDesc();
}
