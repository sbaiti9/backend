package esprit.subscription.repository;

import esprit.subscription.entity.PromoRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromoRedemptionRepository extends JpaRepository<PromoRedemption, Long> {

    long countByPromoCode_IdAndUserId(Long promoCodeId, Long userId);
}
