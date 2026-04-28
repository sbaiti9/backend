package tn.esprit.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.event.entity.Event;
import tn.esprit.event.entity.Reservation;
import tn.esprit.event.entity.ReservationStatus;
import tn.esprit.event.repository.EventRepository;
import tn.esprit.event.repository.ReservationRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final EventRepository eventRepository;
    private final EventService eventService;

    /**
     * ✅ Créer une réservation
     */
    public Reservation createReservation(Reservation reservation) {
        System.out.println("📋 Création réservation pour userId: " + reservation.getUserId());

        // Vérifier que l'utilisateur n'a pas déjà réservé cet événement
        boolean alreadyReserved = reservationRepository.existsByUserIdAndEventId(
                reservation.getUserId(),
                reservation.getEvent().getId()
        );

        if (alreadyReserved) {
            throw new RuntimeException("❌ Vous avez déjà réservé cet événement");
        }

        // Vérifier la capacité
        if (!eventService.hasAvailableCapacity(reservation.getEvent().getId())) {
            throw new RuntimeException("❌ Pas de places disponibles");
        }

        // Sauvegarder la réservation
        Reservation savedReservation = reservationRepository.save(reservation);
        System.out.println("✅ Réservation créée: ID=" + savedReservation.getId());

        return savedReservation;
    }

    /**
     * ✅ Récupérer les réservations d'un utilisateur
     */
    public List<Reservation> getUserReservations(Long userId) {
        System.out.println("📋 Récupération réservations pour userId: " + userId);
        return reservationRepository.findByUserId(userId);
    }

    /**
     * ✅ Récupérer les réservations d'un événement
     */
    public List<Reservation> getEventReservations(Long eventId) {
        System.out.println("📋 Récupération réservations pour eventId: " + eventId);
        return reservationRepository.findByEventId(eventId);
    }

    /**
     * ✅ Approuver une réservation (ADMIN)
     */
    public Reservation approveReservation(Long reservationId) {
        System.out.println("✅ Approbation réservation: ID=" + reservationId);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Réservation non trouvée"));

        // Vérifier la capacité
        if (!eventService.hasAvailableCapacity(reservation.getEvent().getId())) {
            throw new RuntimeException("❌ Pas de places disponibles");
        }

        reservation.setStatus(ReservationStatus.APPROVED);
        return reservationRepository.save(reservation);
    }

    /**
     * ✅ Rejeter une réservation (ADMIN)
     */
    public Reservation rejectReservation(Long reservationId) {
        System.out.println("❌ Rejet réservation: ID=" + reservationId);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Réservation non trouvée"));

        reservation.setStatus(ReservationStatus.REJECTED);
        return reservationRepository.save(reservation);
    }

    /**
     * ✅ Annuler une réservation (USER)
     */
    public void cancelReservation(Long reservationId, Long userId) {
        System.out.println("❌ Annulation réservation: ID=" + reservationId);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Réservation non trouvée"));

        // Vérifier que c'est l'utilisateur qui a fait la réservation
        if (!reservation.getUserId().equals(userId)) {
            throw new RuntimeException("❌ Vous ne pouvez pas annuler cette réservation");
        }

        reservationRepository.deleteById(reservationId);
        System.out.println("✅ Réservation annulée");
    }

    /**
     * ✅ Obtenir une réservation par ID
     */
    public Reservation getReservationById(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Réservation non trouvée"));
    }

    /**
     * ✅ Vérifier si un utilisateur a déjà réservé un événement
     */
    public boolean hasUserReservedEvent(Long userId, Long eventId) {
        return reservationRepository.existsByUserIdAndEventId(userId, eventId);
    }

    /**
     * ✅ Compter les réservations approuvées d'un événement
     */
    public Long countApprovedReservations(Long eventId) {
        return reservationRepository.countApprovedReservationsByEventId(eventId);
    }

    /**
     * ✅ Compter les réservations en attente d'un événement
     */
    public Long countPendingReservations(Long eventId) {
        return reservationRepository.countPendingReservationsByEventId(eventId);
    }

    /**
     * ✅ Marquer les réservations comme vues
     */
    public void markReservationsAsSeen(Long userId) {
        List<Reservation> unseenReservations = reservationRepository.findByUserIdAndSeenByUserFalse(userId);
        unseenReservations.forEach(r -> r.setSeenByUser(true));
        reservationRepository.saveAll(unseenReservations);
    }

    /**
     * ✅ Obtenir les réservations non vues
     */
    public List<Reservation> getUnseenReservations(Long userId) {
        return reservationRepository.findByUserIdAndSeenByUserFalse(userId);
    }
}