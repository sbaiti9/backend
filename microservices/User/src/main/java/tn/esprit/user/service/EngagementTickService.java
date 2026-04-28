package tn.esprit.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.user.entity.PlatformEngagementTier;
import tn.esprit.user.entity.Users;
import tn.esprit.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Crédite des points d’engagement tant que l’utilisateur est actif sur la plateforme
 * (appel périodique côté client, typiquement toutes les {@value #DEFAULT_INTERVAL_SECONDS} secondes).
 */
@Service
public class EngagementTickService {

    public static final int DEFAULT_INTERVAL_SECONDS = 10;
    public static final int DEFAULT_POINTS_PER_TICK = 10;

    private final UserRepository userRepository;

    @Value("${skillio.engagement.tick-interval-seconds:10}")
    private int tickIntervalSeconds;

    @Value("${skillio.engagement.tick-points:10}")
    private int pointsPerTick;

    /** Limite de points gagnés via les ticks sur une fenêtre glissante d’1 h (anti-abus). */
    @Value("${skillio.engagement.tick-max-points-per-hour:3600}")
    private int maxPointsPerHourFromTicks;

    public EngagementTickService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record TickResult(
            boolean awarded,
            int pointsAdded,
            int totalPoints,
            String tier,
            String tierLabelFr,
            int waitSeconds,
            String messageFr
    ) {}

    @Transactional
    public TickResult tryAwardTick(Long userId) {
        Users u = userRepository.findById(userId).orElseThrow();
        LocalDateTime now = LocalDateTime.now();

        int interval = Math.max(5, tickIntervalSeconds);
        int pts = Math.max(1, pointsPerTick);

        LocalDateTime last = u.getLastEngagementTickAt();
        if (last != null) {
            long secondsSince = ChronoUnit.SECONDS.between(last, now);
            if (secondsSince < interval) {
                int wait = interval - (int) secondsSince;
                return new TickResult(
                        false,
                        0,
                        Optional.ofNullable(u.getPlatformEngagementPoints()).orElse(0),
                        PlatformEngagementTier.fromTotalPoints(
                                        Optional.ofNullable(u.getPlatformEngagementPoints()).orElse(0))
                                .name(),
                        PlatformEngagementTier.fromTotalPoints(
                                        Optional.ofNullable(u.getPlatformEngagementPoints()).orElse(0))
                                .getLabelFr(),
                        Math.max(1, wait),
                        "Attendez encore " + wait + " s avant le prochain crédit.");
            }
        }

        LocalDateTime hourStart = now.truncatedTo(ChronoUnit.HOURS);
        if (u.getEngagementTickHourStarted() == null
                || !u.getEngagementTickHourStarted().equals(hourStart)) {
            u.setEngagementTickHourStarted(hourStart);
            u.setEngagementTickPointsThisHour(0);
        }
        int inHour = Optional.ofNullable(u.getEngagementTickPointsThisHour()).orElse(0);
        if (inHour + pts > maxPointsPerHourFromTicks) {
            int total = Optional.ofNullable(u.getPlatformEngagementPoints()).orElse(0);
            PlatformEngagementTier t = PlatformEngagementTier.fromTotalPoints(total);
            return new TickResult(
                    false,
                    0,
                    total,
                    t.name(),
                    t.getLabelFr(),
                    secondsUntilNextHour(now),
                    "Plafond horaire de points (ticks) atteint. Réessayez plus tard.");
        }

        int prev = Optional.ofNullable(u.getPlatformEngagementPoints()).orElse(0);
        int next = prev + pts;
        u.setPlatformEngagementPoints(next);
        u.setEngagementTickPointsThisHour(inHour + pts);
        u.setLastEngagementTickAt(now);
        userRepository.save(u);

        PlatformEngagementTier tier = PlatformEngagementTier.fromTotalPoints(next);
        return new TickResult(
                true,
                pts,
                next,
                tier.name(),
                tier.getLabelFr(),
                interval,
                "+" + pts + " points (activité sur la plateforme). Palier : " + tier.getLabelFr() + ".");
    }

    private static int secondsUntilNextHour(LocalDateTime now) {
        LocalDateTime next = now.truncatedTo(ChronoUnit.HOURS).plusHours(1);
        return (int) Math.max(1, ChronoUnit.SECONDS.between(now, next));
    }
}
