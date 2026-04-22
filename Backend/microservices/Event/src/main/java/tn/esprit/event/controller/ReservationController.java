package tn.esprit.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.event.entity.Reservation;
import tn.esprit.event.service.ReservationService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/event/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /**
     * ✅ POST /event/reservations/create - Créer une réservation
     */
    @PostMapping("/create")
    public ResponseEntity<?> createReservation(@RequestBody Reservation reservation) {
        try {
            System.out.println("📋 POST /event/reservations/create");
            System.out.println("   userId: " + reservation.getUserId());
            System.out.println("   eventId: " + reservation.getEvent().getId());

            Reservation createdReservation = reservationService.createReservation(reservation);

            return ResponseEntity.status(201).body(Map.of(
                    "message", "✅ Réservation créée avec succès!",
                    "reservation", createdReservation
            ));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ GET /event/reservations/user/{userId} - Récupérer les réservations d'un utilisateur
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserReservations(@PathVariable Long userId) {
        try {
            System.out.println("📋 GET /event/reservations/user/" + userId);
            List<Reservation> reservations = reservationService.getUserReservations(userId);

            return ResponseEntity.ok(Map.of(
                    "reservations", reservations,
                    "total", reservations.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ GET /event/reservations/event/{eventId} - Récupérer les réservations d'un événement
     * ⚠️ ADMIN ONLY
     */
    @GetMapping("/event/{eventId}")
    public ResponseEntity<?> getEventReservations(@PathVariable Long eventId) {
        try {
            System.out.println("📋 GET /event/reservations/event/" + eventId);
            List<Reservation> reservations = reservationService.getEventReservations(eventId);

            return ResponseEntity.ok(Map.of(
                    "reservations", reservations,
                    "total", reservations.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ GET /event/reservations/{id} - Récupérer une réservation par ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getReservationById(@PathVariable Long id) {
        try {
            System.out.println("📋 GET /event/reservations/" + id);
            Reservation reservation = reservationService.getReservationById(id);

            return ResponseEntity.ok(reservation);
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "❌ Réservation non trouvée"
            ));
        }
    }

    /**
     * ✅ PUT /event/reservations/{id}/approve - Approuver une réservation
     * ⚠️ ADMIN ONLY
     */
    @PutMapping("/{id}/approve")
    public ResponseEntity<?> approveReservation(@PathVariable Long id) {
        try {
            System.out.println("✅ PUT /event/reservations/" + id + "/approve");
            Reservation approvedReservation = reservationService.approveReservation(id);

            return ResponseEntity.ok(Map.of(
                    "message", "✅ Réservation approuvée!",
                    "reservation", approvedReservation
            ));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ PUT /event/reservations/{id}/reject - Rejeter une réservation
     * ⚠️ ADMIN ONLY
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectReservation(@PathVariable Long id) {
        try {
            System.out.println("❌ PUT /event/reservations/" + id + "/reject");
            Reservation rejectedReservation = reservationService.rejectReservation(id);

            return ResponseEntity.ok(Map.of(
                    "message", "✅ Réservation rejetée!",
                    "reservation", rejectedReservation
            ));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ DELETE /event/reservations/{id}/cancel - Annuler une réservation
     */
    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<?> cancelReservation(@PathVariable Long id, @RequestParam Long userId) {
        try {
            System.out.println("❌ DELETE /event/reservations/" + id + "/cancel");
            reservationService.cancelReservation(id, userId);

            return ResponseEntity.ok(Map.of(
                    "message", "✅ Réservation annulée!"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ GET /event/reservations/check/{userId}/{eventId} - Vérifier si l'utilisateur a déjà réservé
     */
    @GetMapping("/check/{userId}/{eventId}")
    public ResponseEntity<?> hasUserReservedEvent(@PathVariable Long userId, @PathVariable Long eventId) {
        try {
            System.out.println("📋 GET /event/reservations/check/" + userId + "/" + eventId);
            boolean hasReserved = reservationService.hasUserReservedEvent(userId, eventId);

            return ResponseEntity.ok(Map.of(
                    "hasReserved", hasReserved,
                    "message", hasReserved ? "Vous avez déjà réservé" : "Vous pouvez réserver"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ GET /event/reservations/event/{eventId}/approved - Compter les réservations approuvées
     */
    @GetMapping("/event/{eventId}/approved")
    public ResponseEntity<?> countApprovedReservations(@PathVariable Long eventId) {
        try {
            System.out.println("📊 GET /event/reservations/event/" + eventId + "/approved");
            Long count = reservationService.countApprovedReservations(eventId);

            return ResponseEntity.ok(Map.of(
                    "approvedCount", count
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ GET /event/reservations/event/{eventId}/pending - Compter les réservations en attente
     */
    @GetMapping("/event/{eventId}/pending")
    public ResponseEntity<?> countPendingReservations(@PathVariable Long eventId) {
        try {
            System.out.println("📊 GET /event/reservations/event/" + eventId + "/pending");
            Long count = reservationService.countPendingReservations(eventId);

            return ResponseEntity.ok(Map.of(
                    "pendingCount", count
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ PUT /event/reservations/user/{userId}/mark-seen - Marquer les réservations comme vues
     */
    @PutMapping("/user/{userId}/mark-seen")
    public ResponseEntity<?> markReservationsAsSeen(@PathVariable Long userId) {
        try {
            System.out.println("✅ PUT /event/reservations/user/" + userId + "/mark-seen");
            reservationService.markReservationsAsSeen(userId);

            return ResponseEntity.ok(Map.of(
                    "message", "✅ Réservations marquées comme vues!"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ GET /event/reservations/user/{userId}/unseen - Obtenir les réservations non vues
     */
    @GetMapping("/user/{userId}/unseen")
    public ResponseEntity<?> getUnseenReservations(@PathVariable Long userId) {
        try {
            System.out.println("📋 GET /event/reservations/user/" + userId + "/unseen");
            List<Reservation> reservations = reservationService.getUnseenReservations(userId);

            return ResponseEntity.ok(Map.of(
                    "reservations", reservations,
                    "total", reservations.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }
}