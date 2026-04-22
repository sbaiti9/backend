package tn.esprit.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.user.entity.GeoAuthEvent;
import tn.esprit.user.entity.GeoEventType;
import tn.esprit.user.entity.PlatformEngagementTier;
import tn.esprit.user.entity.Users;
import tn.esprit.user.repository.GeoAuthEventRepository;
import tn.esprit.user.repository.UserRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Estime le temps passé sur la plateforme entre deux connexions (événements LOGIN),
 * attribue des points d’engagement Skillio, et optionnellement un court message via Groq.
 */
@Service
public class PlatformEngagementService {

    private static final Logger log = LoggerFactory.getLogger(PlatformEngagementService.class);

    /** Plafond raisonnable d’une « session » entre deux LOGIN (évite d’attribuer des jours entiers). */
    public static final int MAX_SESSION_MINUTES_CAP = 8 * 60;

    /** Points de bienvenue si aucune connexion LOGIN précédente en base. */
    public static final int WELCOME_POINTS = 5;

    /** Reconnexion trop rapide (&lt; 5 min) : pas de points (anti-spam). */
    public static final int MIN_MINUTES_FOR_ACTIVITY_POINTS = 5;

    /** 1 point par tranche de 10 minutes (après plafond). */
    public static final int MINUTES_PER_POINT = 10;

    /** Plafond de points gagnés à une seule connexion. */
    public static final int MAX_POINTS_PER_LOGIN = 50;

    private static final String GROQ_SYSTEM = """
            Tu es Skillio (plateforme e-learning). Réponds uniquement avec un JSON valide :
            {"messageFr":"une phrase courte et positive en français","niveau":"Bronze|Argent|Or"}.
            Le champ niveau doit être EXACTEMENT le même palier que palierServeur (BRONZE→Bronze, SILVER→Argent, GOLD→Or).
            Ne modifie jamais les nombres fournis — style motivation uniquement.""";

    private final GeoAuthEventRepository geoAuthEventRepository;
    private final UserRepository userRepository;
    private final GroqChatClient groqChatClient;
    private final ObjectMapper objectMapper;

    public PlatformEngagementService(GeoAuthEventRepository geoAuthEventRepository,
                                     UserRepository userRepository,
                                     GroqChatClient groqChatClient,
                                     ObjectMapper objectMapper) {
        this.geoAuthEventRepository = geoAuthEventRepository;
        this.userRepository = userRepository;
        this.groqChatClient = groqChatClient;
        this.objectMapper = objectMapper;
    }

    public record EngagementSnapshot(
            int minutesEstimated,
            int pointsEarnedThisLogin,
            int totalPoints,
            PlatformEngagementTier tier,
            String aiMessageFr
    ) {}

    /**
     * À appeler après authentification réussie, <strong>avant</strong> d’enregistrer le nouveau LOGIN géo.
     */
    @Transactional
    public EngagementSnapshot processLoginEngagement(Long userId) {
        Users user = userRepository.findById(userId).orElseThrow();
        LocalDateTime now = LocalDateTime.now();

        Optional<GeoAuthEvent> prevLoginOpt =
                geoAuthEventRepository.findTopByUserIdAndEventTypeOrderByCreatedAtDesc(userId, GeoEventType.LOGIN);

        int minutesEstimated = 0;
        int pointsEarned = 0;

        if (prevLoginOpt.isEmpty()) {
            pointsEarned = WELCOME_POINTS;
        } else {
            GeoAuthEvent prev = prevLoginOpt.get();
            long rawMinutes = Duration.between(prev.getCreatedAt(), now).toMinutes();
            if (rawMinutes < MIN_MINUTES_FOR_ACTIVITY_POINTS) {
                minutesEstimated = (int) Math.max(0, rawMinutes);
                pointsEarned = 0;
            } else {
                minutesEstimated = (int) Math.min(rawMinutes, MAX_SESSION_MINUTES_CAP);
                pointsEarned = Math.min(MAX_POINTS_PER_LOGIN, Math.max(1, minutesEstimated / MINUTES_PER_POINT));
            }
        }

        int previousTotal = Optional.ofNullable(user.getPlatformEngagementPoints()).orElse(0);
        int totalPoints = previousTotal + pointsEarned;
        user.setPlatformEngagementPoints(totalPoints);
        userRepository.save(user);

        PlatformEngagementTier tier = PlatformEngagementTier.fromTotalPoints(totalPoints);

        String aiMessage;
        if (pointsEarned <= 0 && minutesEstimated < MIN_MINUTES_FOR_ACTIVITY_POINTS) {
            aiMessage = fallbackMessageFr(minutesEstimated, pointsEarned, totalPoints, tier);
        } else {
            aiMessage = buildAiMessageFr(minutesEstimated, pointsEarned, totalPoints, user.getEmail(), tier);
        }
        return new EngagementSnapshot(minutesEstimated, pointsEarned, totalPoints, tier, aiMessage);
    }

    private String buildAiMessageFr(int minutes, int earned, int total, String email, PlatformEngagementTier tier) {
        String safeMail = escapeJson(email == null ? "" : email);
        String userJson = String.format(
                "{\"minutesSessionEstimees\":%d,\"pointsGagnesCetteConnexion\":%d,\"pointsTotaux\":%d,"
                        + "\"palierServeur\":\"%s\",\"email\":\"%s\"}",
                minutes, earned, total, tier.name(), safeMail);

        Optional<String> groqJson = groqChatClient.completeJson(GROQ_SYSTEM, userJson, 0.35);
        if (groqJson.isPresent()) {
            try {
                JsonNode root = objectMapper.readTree(groqJson.get());
                String msg = root.path("messageFr").asText("");
                if (!msg.isBlank()) {
                    String niveau = root.path("niveau").asText("");
                    if (!niveau.isBlank()) {
                        return msg.trim() + " — Palier Skillio : " + tier.getLabelFr() + ".";
                    }
                    return msg.trim() + " — Palier Skillio : " + tier.getLabelFr() + ".";
                }
            } catch (Exception e) {
                log.debug("Engagement Groq JSON parse failed: {}", e.getMessage());
            }
        }
        return fallbackMessageFr(minutes, earned, total, tier);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String fallbackMessageFr(int minutes, int earned, int total, PlatformEngagementTier tier) {
        String palier = " Palier Skillio : " + tier.getLabelFr() + ".";
        if (earned <= 0) {
            return "Bonne reconnexion sur Skillio ! Continuez à progresser pour cumuler des points." + palier;
        }
        if (minutes > 0) {
            return String.format(
                    "Temps d’activité estimé depuis votre dernière connexion : environ %d min. "
                            + "Vous gagnez %d point(s) ; total Skillio : %d.%s",
                    minutes, earned, total, palier);
        }
        return String.format(
                "Bienvenue sur Skillio ! Vous recevez %d point(s) de bienvenue. Total : %d.%s",
                earned, total, palier);
    }
}
