package esprit.subscription.repository;

import esprit.subscription.entity.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    List<PaymentRecord> findByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, String status);

    @Query("SELECT COUNT(p) FROM PaymentRecord p WHERE p.userId = :userId AND UPPER(COALESCE(p.status,'')) = 'SUCCESS'")
    long countSuccessfulByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(p) FROM PaymentRecord p WHERE p.userId = :userId AND UPPER(COALESCE(p.status,'')) = 'FAILED'")
    long countFailedByUserId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentRecord p WHERE p.userId = :userId "
            + "AND UPPER(COALESCE(p.status,'')) = 'SUCCESS'")
    Double sumSuccessfulAmountByUserId(@Param("userId") Long userId);

    Optional<PaymentRecord> findFirstByExternalPaymentRefAndStatus(String externalPaymentRef, String status);
}
