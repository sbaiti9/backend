package esprit.subscription.repository;

import esprit.subscription.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RepositoryRestResource(exported = false)
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUserId(Long userId);
    Optional<Subscription> findFirstByUserIdAndStatusIn(Long userId, List<String> status);

    Optional<Subscription> findTopByUserIdAndStatusInOrderByStartDateDesc(Long userId, List<String> statuses);

    @Query("SELECT s FROM Subscription s WHERE s.status = 'PAUSED' AND s.pauseEndAt IS NOT NULL AND s.pauseEndAt <= :now")
    List<Subscription> findPausedDueToResume(@Param("now") LocalDateTime now);

    @Query("SELECT s FROM Subscription s WHERE s.userId = :userId AND s.status = 'ACTIVE' ORDER BY s.startDate DESC")
    Optional<Subscription> findActiveByUserId(@Param("userId") Long userId);

    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' AND s.autoRenew = true AND s.endDate IS NOT NULL "
            + "AND s.endDate >= :start AND s.endDate < :end")
    List<Subscription> findActiveAutoRenewEndingBetween(@Param("start") LocalDateTime start,
                                                         @Param("end") LocalDateTime end);

    @Query("SELECT p.name, COUNT(s) FROM Subscription s JOIN s.pricingPlan p WHERE s.status = 'ACTIVE' GROUP BY p.name ORDER BY COUNT(s) DESC")
    List<Object[]> countActiveByPlanName();

    @Query("SELECT p.id, p.name, COUNT(s) FROM Subscription s JOIN s.pricingPlan p WHERE s.status = 'ACTIVE' "
            + "GROUP BY p.id, p.name ORDER BY COUNT(s) DESC")
    List<Object[]> countActivePlansWithIds();

    @Query("SELECT p.id, p.name, COUNT(s) FROM Subscription s JOIN s.pricingPlan p WHERE s.status = 'ACTIVE' "
            + "AND UPPER(TRIM(COALESCE(s.userRole,''))) = UPPER(TRIM(:role)) GROUP BY p.id, p.name ORDER BY COUNT(s) DESC")
    List<Object[]> countActiveByPlanForRole(@Param("role") String role);

    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.status = 'ACTIVE' "
            + "AND UPPER(TRIM(COALESCE(s.userRole,''))) = UPPER(TRIM(:role))")
    long countActiveByUserRoleNormalized(@Param("role") String role);

    // ── Stats ────────────────────────────────────────────────────────

    long countByStatus(String status);

    @Query("SELECT s.billingCycle, COUNT(s) FROM Subscription s GROUP BY s.billingCycle ORDER BY COUNT(s) DESC")
    List<Object[]> countByBillingCycle();

    @Query("SELECT s.userRole, COUNT(s) FROM Subscription s GROUP BY s.userRole ORDER BY COUNT(s) DESC")
    List<Object[]> countByUserRole();

    @Query("SELECT s.status, COUNT(s) FROM Subscription s GROUP BY s.status")
    List<Object[]> countGroupByStatus();

    // Utilise le nom du plan via pricingPlan.name (adapter selon votre entité)
    @Query("SELECT p.name, COUNT(s) FROM Subscription s JOIN s.pricingPlan p GROUP BY p.name ORDER BY COUNT(s) DESC")
    List<Object[]> countByPlanName();

    // Revenu MONTHLY : somme des prix mensuels des abonnements ACTIVE mensuels
    @Query("SELECT COALESCE(SUM(p.monthlyPrice), 0) FROM Subscription s JOIN s.pricingPlan p WHERE s.status = 'ACTIVE' AND s.billingCycle = 'MONTHLY'")
    Double calculateMonthlyRevenue();

    // Revenu YEARLY : somme des prix annuels des abonnements ACTIVE annuels
    @Query("SELECT COALESCE(SUM(p.yearlyPrice), 0) FROM Subscription s JOIN s.pricingPlan p WHERE s.status = 'ACTIVE' AND s.billingCycle = 'YEARLY'")
    Double calculateYearlyRevenue();

    /** Tous les abonnements actifs avec plan (pour MRR / historique). */
    @Query("SELECT s FROM Subscription s JOIN FETCH s.pricingPlan p WHERE s.status = 'ACTIVE'")
    List<Subscription> findAllActiveWithPlans();

    List<Subscription> findByUserIdOrderByCreatedAtAsc(Long userId);

    @Query("SELECT DISTINCT s.userId FROM Subscription s")
    List<Long> findDistinctUserIds();
}
