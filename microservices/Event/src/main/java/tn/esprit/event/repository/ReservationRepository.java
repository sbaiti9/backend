package tn.esprit.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.event.entity.Reservation;
import tn.esprit.event.entity.ReservationStatus;

import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // ✅ Trouver les réservations d'un utilisateur
    List<Reservation> findByUserId(Long userId);

    // ✅ Trouver les réservations d'un événement
    List<Reservation> findByEventId(Long eventId);

    // ✅ Trouver les réservations approuvées d'un événement
    List<Reservation> findByEventIdAndStatus(Long eventId, ReservationStatus status);

    // ✅ Vérifier si un utilisateur a déjà réservé un événement
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Reservation r WHERE r.userId = :userId AND r.event.id = :eventId")
    boolean existsByUserIdAndEventId(@Param("userId") Long userId, @Param("eventId") Long eventId);

    // ✅ Compter les réservations approuvées d'un événement
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.event.id = :eventId AND r.status = 'APPROVED'")
    Long countApprovedReservationsByEventId(@Param("eventId") Long eventId);

    // ✅ Compter les réservations en attente d'un événement
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.event.id = :eventId AND r.status = 'PENDING'")
    Long countPendingReservationsByEventId(@Param("eventId") Long eventId);

    // ✅ Trouver les réservations non vues par l'utilisateur
    List<Reservation> findByUserIdAndSeenByUserFalse(Long userId);

    // ✅ Supprimer les réservations d'un événement
    void deleteByEventId(Long eventId);
}