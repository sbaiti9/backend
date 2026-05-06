package tn.esprit.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.event.entity.Event;
import tn.esprit.event.entity.Reservation;
import tn.esprit.event.entity.ReservationStatus;
import tn.esprit.event.repository.EventRepository;
import tn.esprit.event.repository.ReservationRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class EventService {

    private final EventRepository eventRepository;
    private final ReservationRepository reservationRepository;
    private final EventSearchService eventSearchService;

    @Autowired
    private JavaMailSender mailSender;

    /**
     * ✅ Créer un nouvel événement et envoyer email à tous les utilisateurs
     */
    @CacheEvict(value = {"events", "events-all", "events-upcoming", "events-byUserId"}, allEntries = true)
    public Event createEvent(Event event) {
        System.out.println("📋 Création événement: " + event.getTitle());

        // Sauvegarder l'événement
        Event savedEvent = eventRepository.save(event);
        System.out.println("✅ Événement créé: ID=" + savedEvent.getId());
        eventSearchService.index(savedEvent);

        // 📧 Envoyer email à tous les utilisateurs
        sendEventCreationEmailToAllUsers(savedEvent);

        return savedEvent;
    }

    /**
     * ✅ Récupérer tous les événements
     */
    @Cacheable(value = "events-all", key = "'all'")
    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    /**
     * ✅ Récupérer les événements à venir
     */
    @Cacheable(value = "events-upcoming", key = "'all'")
    public List<Event> getUpcomingEvents() {
        return eventRepository.findUpcomingEvents();
    }

    /**
     * ✅ Récupérer les événements d'un organisateur
     */
    @Cacheable(value = "events-byUserId", key = "#userId")
    public List<Event> getEventsByUserId(Long userId) {
        return eventRepository.findByUserId(userId);
    }

    /**
     * ✅ Récupérer un événement par ID
     */
    @Cacheable(value = "events", key = "#eventId")
    public Event getEventById(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Événement non trouvé: " + eventId));
    }

    /**
     * ✅ Rechercher les événements par titre
     */
    public List<Event> searchEventsByTitle(String title) {
        return eventRepository.findByTitleContainingIgnoreCase(title);
    }

    /**
     * ✅ Rechercher les événements par localisation
     */
    public List<Event> searchEventsByLocation(String location) {
        return eventRepository.findByLocationContainingIgnoreCase(location);
    }

    /**
     * ✅ Rechercher les événements par date
     */
    public List<Event> searchEventsByDate(LocalDate startDate, LocalDate endDate) {
        return eventRepository.findByStartDateBetween(startDate, endDate);
    }

    /**
     * ✅ Modifier un événement
     */
    @CacheEvict(value = {"events", "events-all", "events-upcoming", "events-byUserId"}, allEntries = true)
    public Event updateEvent(Long eventId, Event eventDetails) {
        System.out.println("📝 Modification événement: ID=" + eventId);

        Event event = getEventById(eventId);

        if (eventDetails.getTitle() != null) event.setTitle(eventDetails.getTitle());
        if (eventDetails.getStartDate() != null) event.setStartDate(eventDetails.getStartDate());
        if (eventDetails.getEndDate() != null) event.setEndDate(eventDetails.getEndDate());
        if (eventDetails.getLocation() != null) event.setLocation(eventDetails.getLocation());
        if (eventDetails.getCapacity() != null) event.setCapacity(eventDetails.getCapacity());
        if (eventDetails.getPrice() != null) event.setPrice(eventDetails.getPrice());
        if (eventDetails.getImageBase64() != null) event.setImageBase64(eventDetails.getImageBase64());

        Event saved = eventRepository.save(event);
        eventSearchService.index(saved);
        return saved;
    }

    /**
     * ✅ Supprimer un événement et ses réservations
     */
    @CacheEvict(value = {"events", "events-all", "events-upcoming", "events-byUserId"}, allEntries = true)
    public void deleteEvent(Long eventId) {
        System.out.println("🗑️ Suppression événement: ID=" + eventId);

        // Supprimer les réservations
        reservationRepository.deleteByEventId(eventId);

        // Supprimer l'événement
        eventRepository.deleteById(eventId);
        eventSearchService.deleteFromIndex(eventId);

        System.out.println("✅ Événement supprimé");
    }

    /**
     * ✅ Obtenir les places disponibles d'un événement
     */
    public Integer getAvailableCapacity(Long eventId) {
        Event event = getEventById(eventId);
        Long approvedReservations = reservationRepository.countApprovedReservationsByEventId(eventId);
        return event.getCapacity() - approvedReservations.intValue();
    }

    /**
     * ✅ Envoyer email à tous les utilisateurs lors de la création d'événement
     * 📧 Utilise EmailService
     */
    private void sendEventCreationEmailToAllUsers(Event event) {
        System.out.println("📧 Envoi email de création d'événement à tous les utilisateurs...");

        // TODO: Récupérer tous les emails des utilisateurs via UserServiceClient Feign
        // Pour maintenant, utiliser un email de test

        String subject = "🎉 Nouvel Événement: " + event.getTitle();
        String body = buildEventCreationEmailBody(event);

        // Créer le message
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@event-system.com");
        message.setSubject(subject);
        message.setText(body);

        // TODO: Ajouter les destinataires
        message.setTo("issmailchouikhi6@gmail.com");

        try {
             mailSender.send(message);
            System.out.println("✅ Email créé (à envoyer quand les destinataires sont disponibles)");
        } catch (Exception e) {
            System.err.println("❌ Erreur envoi email: " + e.getMessage());
        }
    }

    /**
     * ✅ Construire le corps de l'email de création d'événement
     */
    private String buildEventCreationEmailBody(Event event) {
        return "Bonjour,\n\n" +
                "Un nouvel événement a été créé !\n\n" +
                "🎉 Titre: " + event.getTitle() + "\n" +
                "📍 Localisation: " + event.getLocation() + "\n" +
                "📅 Date de début: " + event.getStartDate() + "\n" +
                "📅 Date de fin: " + event.getEndDate() + "\n" +
                "👥 Capacité: " + event.getCapacity() + " places\n" +
                "💰 Prix: " + event.getPrice() + " DT\n\n" +
                "Connectez-vous pour réserver votre place !\n\n" +
                "Cordialement,\n" +
                "L'équipe Event System";
    }

    /**
     * ✅ Compter les événements d'un utilisateur
     */
    public Long countEventsByUserId(Long userId) {
        return eventRepository.countByUserId(userId);
    }

    /**
     * ✅ Vérifier la capacité d'un événement
     */
    public boolean hasAvailableCapacity(Long eventId) {
        return getAvailableCapacity(eventId) > 0;
    }
}