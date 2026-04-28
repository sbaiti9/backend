package tn.esprit.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.event.entity.Event;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    // ✅ Trouver les événements d'un utilisateur (admin/organisateur)
    List<Event> findByUserId(Long userId);

    // ✅ Trouver les événements par titre (recherche)
    List<Event> findByTitleContainingIgnoreCase(String title);

    // ✅ Trouver les événements entre deux dates
    List<Event> findByStartDateBetween(LocalDate startDate, LocalDate endDate);

    // ✅ Trouver les événements à venir
    @Query("SELECT e FROM Event e WHERE e.startDate >= CURRENT_DATE ORDER BY e.startDate ASC")
    List<Event> findUpcomingEvents();

    // ✅ Trouver les événements passés
    @Query("SELECT e FROM Event e WHERE e.endDate < CURRENT_DATE ORDER BY e.startDate DESC")
    List<Event> findPastEvents();

    // ✅ Trouver les événements par localisation
    List<Event> findByLocationContainingIgnoreCase(String location);

    // ✅ Compter les événements d'un utilisateur
    Long countByUserId(Long userId);
}