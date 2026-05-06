package tn.esprit.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.event.entity.Event;
import tn.esprit.event.service.EventService;
import tn.esprit.event.service.EventSearchService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/event")
@RequiredArgsConstructor

public class EventController {

    private final EventService eventService;
    private final EventSearchService eventSearchService;

    /**
     * ✅ POST /event/create - Créer un nouvel événement
     * ⚠️ ADMIN ONLY
     */
    @PostMapping("/create")
    public ResponseEntity<?> createEvent(@RequestBody Event event) {
        try {
            System.out.println("📋 POST /event/create");
            System.out.println("   Titre: " + event.getTitle());
            System.out.println("   Localisation: " + event.getLocation());
            System.out.println("   Capacité: " + event.getCapacity());

            Event createdEvent = eventService.createEvent(event);

            return ResponseEntity.status(201).body(Map.of(
                    "message", "✅ Événement créé avec succès!",
                    "event", createdEvent
            ));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ GET /event/all - Récupérer tous les événements
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllEvents() {
        try {
            System.out.println("📋 GET /event/all");
            List<Event> events = eventService.getAllEvents();

            return ResponseEntity.ok(Map.of(
                    "events", events,
                    "total", events.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ GET /event/search?q= - Full-text search (Elasticsearch)
     */
    @GetMapping("/search")
    public List<Event> search(@RequestParam("q") String q) {
        return eventSearchService.search(q);
    }

    /**
     * ✅ GET /event/upcoming - Récupérer les événements à venir
     */
    @GetMapping("/upcoming")
    public ResponseEntity<?> getUpcomingEvents() {
        try {
            System.out.println("📋 GET /event/upcoming");
            List<Event> events = eventService.getUpcomingEvents();

            return ResponseEntity.ok(Map.of(
                    "events", events,
                    "total", events.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ GET /event/{id} - Récupérer un événement par ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getEventById(@PathVariable Long id) {
        try {
            System.out.println("📋 GET /event/" + id);
            Event event = eventService.getEventById(id);

            return ResponseEntity.ok(event);
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "❌ Événement non trouvé"
            ));
        }
    }

    /**
     * ✅ GET /event/user/{userId} - Récupérer les événements d'un organisateur
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getEventsByUserId(@PathVariable Long userId) {
        try {
            System.out.println("📋 GET /event/user/" + userId);
            List<Event> events = eventService.getEventsByUserId(userId);

            return ResponseEntity.ok(Map.of(
                    "events", events,
                    "total", events.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ GET /event/search/title - Rechercher par titre
     */
    @GetMapping("/search/title")
    public ResponseEntity<?> searchByTitle(@RequestParam String title) {
        try {
            System.out.println("📋 GET /event/search/title?title=" + title);
            List<Event> events = eventService.searchEventsByTitle(title);

            return ResponseEntity.ok(Map.of(
                    "events", events,
                    "total", events.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ GET /event/search/location - Rechercher par localisation
     */
    @GetMapping("/search/location")
    public ResponseEntity<?> searchByLocation(@RequestParam String location) {
        try {
            System.out.println("📋 GET /event/search/location?location=" + location);
            List<Event> events = eventService.searchEventsByLocation(location);

            return ResponseEntity.ok(Map.of(
                    "events", events,
                    "total", events.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ GET /event/search/date - Rechercher par date
     */
    @GetMapping("/search/date")
    public ResponseEntity<?> searchByDate(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        try {
            System.out.println("📋 GET /event/search/date");
            List<Event> events = eventService.searchEventsByDate(startDate, endDate);

            return ResponseEntity.ok(Map.of(
                    "events", events,
                    "total", events.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ PUT /event/{id} - Modifier un événement
     * ⚠️ ADMIN ONLY
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateEvent(@PathVariable Long id, @RequestBody Event eventDetails) {
        try {
            System.out.println("📝 PUT /event/" + id);
            Event updatedEvent = eventService.updateEvent(id, eventDetails);

            return ResponseEntity.ok(Map.of(
                    "message", "✅ Événement modifié avec succès!",
                    "event", updatedEvent
            ));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ DELETE /event/{id} - Supprimer un événement
     * ⚠️ ADMIN ONLY
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEvent(@PathVariable Long id) {
        try {
            System.out.println("🗑️ DELETE /event/" + id);
            eventService.deleteEvent(id);

            return ResponseEntity.ok(Map.of(
                    "message", "✅ Événement supprimé avec succès!"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ GET /event/{id}/available-capacity - Obtenir les places disponibles
     */
    @GetMapping("/{id}/available-capacity")
    public ResponseEntity<?> getAvailableCapacity(@PathVariable Long id) {
        try {
            System.out.println("📊 GET /event/" + id + "/available-capacity");
            Integer available = eventService.getAvailableCapacity(id);

            return ResponseEntity.ok(Map.of(
                    "availableCapacity", available
            ));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "❌ Événement non trouvé"
            ));
        }
    }

    /**
     * ✅ GET /event/{id}/has-capacity - Vérifier si des places sont disponibles
     */
    @GetMapping("/{id}/has-capacity")
    public ResponseEntity<?> hasCapacity(@PathVariable Long id) {
        try {
            System.out.println("📊 GET /event/" + id + "/has-capacity");
            boolean hasCapacity = eventService.hasAvailableCapacity(id);

            return ResponseEntity.ok(Map.of(
                    "hasCapacity", hasCapacity
            ));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "❌ Événement non trouvé"
            ));
        }
    }
}