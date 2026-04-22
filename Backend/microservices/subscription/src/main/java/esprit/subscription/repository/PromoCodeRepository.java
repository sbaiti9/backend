package esprit.subscription.repository;

import esprit.subscription.entity.PromoAudience;
import esprit.subscription.entity.PromoCode;
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
public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {

    Optional<PromoCode> findByCode(String code);

    @Query("SELECT p FROM PromoCode p WHERE UPPER(TRIM(p.code)) = UPPER(TRIM(:raw))")
    Optional<PromoCode> findByCodeNormalized(@Param("raw") String raw);

    @Query("SELECT COUNT(p) > 0 FROM PromoCode p WHERE p.createdByUserId = :userId AND p.createdAt >= :since")
    boolean existsByCreatedByUserIdSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Query("""
            SELECT p FROM PromoCode p WHERE p.createdByUserId = :userId AND p.active = true
            AND (p.expirationDate IS NULL OR p.expirationDate > :now)
            AND (p.maxUses IS NULL OR COALESCE(p.currentUses, 0) < p.maxUses)
            ORDER BY p.createdAt DESC
            """)
    List<PromoCode> findEligibleActiveForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * System / marketing codes ({@code createdByUserId} = Skillio seed user) with {@link PromoAudience#ALL},
     * for “you have a promo” banners when the user has no personal code.
     */
    @Query("""
            SELECT p FROM PromoCode p WHERE p.active = true
            AND p.createdByUserId = :systemUserId
            AND (p.applicableAudience IS NULL OR p.applicableAudience = :audience)
            AND (p.expirationDate IS NULL OR p.expirationDate > :now)
            AND (p.maxUses IS NULL OR COALESCE(p.currentUses, 0) < p.maxUses)
            ORDER BY p.createdAt DESC
            """)
    List<PromoCode> findEligibleSystemCampaigns(
            @Param("systemUserId") Long systemUserId,
            @Param("audience") PromoAudience audience,
            @Param("now") LocalDateTime now);

    List<PromoCode> findByCreatedByUserIdOrderByCreatedAtAsc(Long userId);

    long countByCreatedByUserId(Long userId);
}
