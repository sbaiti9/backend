package esprit.subscription.repository;

import esprit.subscription.entity.AdminNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdminNotificationRepository extends JpaRepository<AdminNotification, Long> {

    List<AdminNotification> findByIsReadFalseOrderByCreatedAtDesc();

    Optional<AdminNotification> findByActionToken(String actionToken);

    long countByIsReadFalse();

    @Modifying
    @Query("UPDATE AdminNotification n SET n.isRead = true WHERE n.id = :id")
    int markReadById(@Param("id") Long id);

    @Modifying
    @Query("UPDATE AdminNotification n SET n.isRead = true WHERE n.isRead = false")
    int markAllRead();
}
