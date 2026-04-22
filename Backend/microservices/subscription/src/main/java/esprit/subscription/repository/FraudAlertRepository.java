package esprit.subscription.repository;

import esprit.subscription.entity.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(exported = false)
public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {
    List<FraudAlert> findByUserId(Long userId);
    List<FraudAlert> findByRiskLevel(String riskLevel);
    List<FraudAlert> findByResolved(boolean resolved);
    List<FraudAlert> findByUserIdAndRiskLevel(Long userId, String riskLevel);
    long countByResolved(boolean resolved);
}
