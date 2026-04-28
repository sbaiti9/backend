package tn.esprit.user.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.user.entity.GeoAuthEvent;
import tn.esprit.user.entity.GeoEventType;
import tn.esprit.user.entity.Users;
import tn.esprit.user.repository.GeoAuthEventRepository;
import tn.esprit.user.repository.UserRepository;
import tn.esprit.user.util.ClientIpResolver;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Enregistre les événements géolocalisés et détecte les déplacements impossibles
 * (vitesse impliquée &gt; {@value #MAX_SPEED_KMH} km/h entre deux points).
 */
@Service
public class GeoSecurityService {

    public static final double MAX_SPEED_KMH = 900.0;

    private static final Logger log = LoggerFactory.getLogger(GeoSecurityService.class);

    private final GeoAuthEventRepository geoAuthRepository;
    private final UserRepository userRepository;
    private final IpGeoLookupService ipGeoLookupService;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:no-reply@skillio.local}")
    private String fromEmail;

    public GeoSecurityService(GeoAuthEventRepository geoAuthRepository,
                              UserRepository userRepository,
                              IpGeoLookupService ipGeoLookupService,
                              ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.geoAuthRepository = geoAuthRepository;
        this.userRepository = userRepository;
        this.ipGeoLookupService = ipGeoLookupService;
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    @Transactional
    public GeoAuthEvent recordEvent(Long userId, GeoEventType type, HttpServletRequest request) {
        String ip = ClientIpResolver.resolve(request);
        return recordEvent(userId, type, ip);
    }

    @Transactional
    public GeoAuthEvent recordEvent(Long userId, GeoEventType type, String clientIp) {
        Optional<IpGeoLookupService.GeoPoint> geo = ipGeoLookupService.lookup(clientIp);
        return persistEvent(userId, type, clientIp, geo.orElse(null));
    }

    @Transactional
    protected GeoAuthEvent persistEvent(Long userId, GeoEventType type, String ip,
                                        IpGeoLookupService.GeoPoint geo) {
        GeoAuthEvent prev = geoAuthRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);

        GeoAuthEvent ev = new GeoAuthEvent();
        ev.setUserId(userId);
        ev.setEventType(type);
        ev.setIpAddress(ip);
        ev.setImpossibleTravel(false);
        ev.setImpliedSpeedKmh(null);

        if (geo != null) {
            ev.setLatitude(geo.latitude());
            ev.setLongitude(geo.longitude());
            ev.setCity(geo.city());
            ev.setCountry(geo.country());
        }

        if (prev != null && prev.getLatitude() != null && prev.getLongitude() != null
                && ev.getLatitude() != null && ev.getLongitude() != null) {
            double km = haversineKm(prev.getLatitude(), prev.getLongitude(), ev.getLatitude(), ev.getLongitude());
            double hours = hoursBetween(prev.getCreatedAt(), ev.getCreatedAt());
            if (hours <= 0) {
                hours = 1.0 / 3600.0;
            }
            double speed = km / hours;
            ev.setImpliedSpeedKmh(speed);
            if (speed > MAX_SPEED_KMH && km > 50) {
                ev.setImpossibleTravel(true);
                log.warn("Impossible travel user={} {} km in {} h => {} km/h (events {} -> {})",
                        userId, String.format("%.0f", km), String.format("%.4f", hours),
                        String.format("%.0f", speed), prev.getId(), type);
            }
        }

        return geoAuthRepository.save(ev);
    }

    private static double hoursBetween(LocalDateTime a, LocalDateTime b) {
        long seconds = Duration.between(a, b).getSeconds();
        return seconds / 3600.0;
    }

    /** Haversine distance in km. */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double x = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x));
        return R * c;
    }

    public List<GeoAuthEvent> recentEvents(LocalDateTime since) {
        return geoAuthRepository.findByCreatedAtAfterOrderByCreatedAtDesc(since);
    }

    public List<GeoAuthEvent> recentAnomalies(LocalDateTime since) {
        return geoAuthRepository.findByImpossibleTravelIsTrueAndCreatedAtAfterOrderByCreatedAtDesc(since);
    }

    /**
     * Historique admin : derniers événements géo / auth pour un utilisateur (connexion, paiement, formation…).
     */
    @Transactional(readOnly = true)
    public List<GeoAuthEvent> recentEventsForUser(Long userId, int limit) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        int cap = Math.min(Math.max(limit, 1), 200);
        return geoAuthRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, cap, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /**
     * Démo / QA : deux connexions (LOGIN) Tunis puis Paris ~16 min plus tard → alerte déplacement impossible,
     * sans VPN (coordonnées fixes). À n’exposer qu’en environnement contrôlé.
     */
    @Transactional
    public void simulateImpossibleTravelDemo(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(30);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(14);

        double tunLat = 36.8065;
        double tunLon = 10.1815;
        double parLat = 48.8566;
        double parLon = 2.3522;

        GeoAuthEvent e1 = new GeoAuthEvent();
        e1.setUserId(userId);
        e1.setEventType(GeoEventType.LOGIN);
        e1.setIpAddress("198.51.100.10");
        e1.setLatitude(tunLat);
        e1.setLongitude(tunLon);
        e1.setCity("Tunis");
        e1.setCountry("Tunisia");
        e1.setCreatedAt(t1);
        e1.setImpossibleTravel(false);
        e1.setImpliedSpeedKmh(null);
        geoAuthRepository.save(e1);

        GeoAuthEvent e2 = new GeoAuthEvent();
        e2.setUserId(userId);
        e2.setEventType(GeoEventType.LOGIN);
        e2.setIpAddress("198.51.100.20");
        e2.setLatitude(parLat);
        e2.setLongitude(parLon);
        e2.setCity("Paris");
        e2.setCountry("France");
        e2.setCreatedAt(t2);
        double km = haversineKm(tunLat, tunLon, parLat, parLon);
        double hours = Math.max(Duration.between(t1, t2).getSeconds() / 3600.0, 1.0 / 3600.0);
        double speed = km / hours;
        e2.setImpliedSpeedKmh(speed);
        e2.setImpossibleTravel(speed > MAX_SPEED_KMH && km > 50);
        geoAuthRepository.save(e2);
        log.info("simulateImpossibleTravelDemo user={} km={} speedKmH={} flag={}", userId, km, speed, e2.isImpossibleTravel());
    }

    @Transactional
    public Users freezeAndInvalidateTokens(Long userId) {
        Users target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Always rotate token version to invalidate any active sessions.
        int currentTv = target.getTokenVersion() == null ? 0 : target.getTokenVersion();
        target.setTokenVersion(currentTv + 1);
        target.setStatus(tn.esprit.user.entity.Status.FROZEN);

        Users saved = userRepository.saveAndFlush(target);
        if (saved.getStatus() != tn.esprit.user.entity.Status.FROZEN) {
            throw new IllegalStateException("User freeze operation failed for userId=" + userId);
        }
        return saved;
    }

    public record AdminEmailDraft(
            Long userId,
            String to,
            String subject,
            String body,
            String deadlineIso
    ) {}

    public AdminEmailDraft buildFreezeWarningEmailDraft(Long userId) {
        Users u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        String to = u.getEmail();
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("User has no email address");
        }

        GeoAuthEvent anomaly = geoAuthRepository
                .findTopByUserIdAndImpossibleTravelIsTrueOrderByCreatedAtDesc(userId)
                .orElse(null);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusHours(24);
        String deadlineFmt = deadline.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        String location = "activité géographique inhabituelle";
        String eventAt = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        if (anomaly != null) {
            String city = anomaly.getCity() != null ? anomaly.getCity() : "";
            String country = anomaly.getCountry() != null ? anomaly.getCountry() : "";
            String joined = (city + " " + country).trim();
            if (!joined.isBlank()) {
                location = joined;
            }
            if (anomaly.getCreatedAt() != null) {
                eventAt = anomaly.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            }
        }

        String subject = "[Skillio] Verification de securite geographique - reponse sous 24h";
        String body = buildAiStyledWarningBody(u, anomaly, location, eventAt, deadlineFmt);

        return new AdminEmailDraft(userId, to, subject, body, deadline.toString());
    }

    public void sendWarningEmail(Long userId, String to, String subject, String body) {
        if (mailSender == null) {
            throw new IllegalArgumentException("SMTP is not configured for User service (spring.mail.* missing)");
        }
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Recipient email is required");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Email subject is required");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Email body is required");
        }
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromEmail);
        msg.setTo(to.trim());
        msg.setSubject(subject.trim());
        msg.setText(body.trim());
        mailSender.send(msg);
    }

    private String buildAiStyledWarningBody(
            Users user,
            GeoAuthEvent anomaly,
            String location,
            String eventAt,
            String deadlineFmt
    ) {
        String userName = (user.getName() != null && !user.getName().isBlank()) ? user.getName() : "utilisateur";
        String eventType = anomaly != null && anomaly.getEventType() != null ? anomaly.getEventType().name() : "ACTIVITY";
        double speed = anomaly != null && anomaly.getImpliedSpeedKmh() != null ? anomaly.getImpliedSpeedKmh() : 0.0;

        String riskLevel;
        if (speed >= 3000) {
            riskLevel = "critique";
        } else if (speed >= 1500) {
            riskLevel = "élevé";
        } else {
            riskLevel = "modéré";
        }

        String likelyCause = switch (eventType) {
            case "LOGIN" -> "connexion depuis une localisation éloignée en un temps très court";
            case "PAYMENT" -> "activité de paiement géographiquement incohérente";
            default -> "activité inhabituelle détectée par nos contrôles géographiques";
        };

        String securityActions = speed >= 1500
                ? "Nous vous recommandons immédiatement de changer votre mot de passe et d'activer la vérification supplémentaire."
                : "Nous vous recommandons de vérifier vos dernières connexions et de sécuriser votre compte si nécessaire.";

        return String.join("\n",
                "Bonjour " + userName + ",",
                "",
                "Notre analyse de sécurité a détecté un signal de risque " + riskLevel + " sur votre compte Skillio.",
                "Cas détecté : " + likelyCause + ".",
                "Détails : " + location + " (" + eventAt + ")"
                        + (speed > 0 ? ", vitesse estimée ~" + Math.round(speed) + " km/h." : "."),
                "",
                "Merci de nous confirmer dans les 24 heures qu'il s'agit bien de votre activité légitime.",
                "Date limite de réponse : " + deadlineFmt + ".",
                "",
                "Sans réponse avant ce délai, nous pourrons bloquer temporairement votre compte afin de protéger vos données.",
                securityActions,
                "",
                "Si vous reconnaissez l'activité, répondez simplement à cet email.",
                "Si vous ne la reconnaissez pas, signalez-le immédiatement au support Skillio.",
                "",
                "Cordialement,",
                "Administration sécurité Skillio");
    }
}
