package esprit.subscription.repository;

import esprit.subscription.entity.PricingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(exported = false)
public interface PricingPlanRepository extends JpaRepository<PricingPlan, Long> {
    List<PricingPlan> findByIsActiveTrue();
    long countByIsActive(Boolean isActive);
    long countByHighlight(Boolean highlight);

    @Query("SELECT p.name, COUNT(p) FROM PricingPlan p GROUP BY p.name ORDER BY COUNT(p) DESC")
    List<Object[]> countByName();

    @Query("SELECT AVG(p.monthlyPrice) FROM PricingPlan p WHERE p.isActive = true")
    Double findAverageMonthlyPrice();

    @Query("SELECT MIN(p.monthlyPrice) FROM PricingPlan p WHERE p.isActive = true")
    Double findMinMonthlyPrice();

    @Query("SELECT MAX(p.monthlyPrice) FROM PricingPlan p WHERE p.isActive = true")
    Double findMaxMonthlyPrice();

    @Query("SELECT AVG(p.yearlyPrice) FROM PricingPlan p WHERE p.isActive = true")
    Double findAverageYearlyPrice();
}
