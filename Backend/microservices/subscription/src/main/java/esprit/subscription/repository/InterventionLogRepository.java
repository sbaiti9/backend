package esprit.subscription.repository;

import esprit.subscription.entity.InterventionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterventionLogRepository extends JpaRepository<InterventionLog, Long> {

    List<InterventionLog> findByOutcomeIn(List<String> outcomes);

    List<InterventionLog> findByRecommendedAtAfter(java.time.LocalDateTime since);
}
