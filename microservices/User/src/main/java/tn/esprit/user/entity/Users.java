package tn.esprit.user.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;
import java.util.Optional;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Document(indexName = "users")
public class Users {
    @jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Elasticsearch document id (string version of DB id).
     * Stored only in ES, not in the SQL table.
     */
    @Transient
    @Id
    private String esId;
    private String name;

    /**
     * Derived text used for full-text search (ES only).
     * We populate it from other fields before indexing.
     */
    @Transient
    private String description;
    @Column(unique = true)
    private String email;
    private String password;
    @Enumerated(EnumType.STRING)
    private Role role;
    private double average_rating;
    private String adresse;
    private String num_tel ;
    private String photo_de_profile;
    private String prenom;
    private String profession;
    private String specialte;
    @Enumerated(EnumType.STRING)
    private Status status= Status.ACTIVE;

    /** Incrémenté au gel / déconnexion forcée — invalide les JWT existants (claim tv). */
    @Column(nullable = false)
    private Integer tokenVersion = 0;
    @Enumerated(EnumType.STRING)
    private Education_level educationLevel;

    /**
     * Nombre d'échecs de connexion consécutifs (mot de passe invalide).
     * Reset à 0 lors d'une connexion réussie ou à l'expiration du lock.
     */
    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    /**
     * Si non-null et dans le futur, le compte est temporairement bloqué (anti brute-force).
     */
    private LocalDateTime lockUntil;

    /**
     * Points d’engagement Skillio (temps estimé entre connexions + bonus bienvenue), distincts de la fidélisation abonnement.
     */
    @Column(name = "platform_engagement_points")
    private Integer platformEngagementPoints = 0;

    /** Dernier crédit « tick » engagement (intervalle côté client, ex. 10 s). */
    private LocalDateTime lastEngagementTickAt;

    /** Début d’heure UTC (tronquée) pour le compteur de points issus des ticks. */
    private LocalDateTime engagementTickHourStarted;

    /** Points gagnés via les ticks pendant {@code engagementTickHourStarted}. */
    @Column(name = "engagement_tick_points_this_hour")
    private Integer engagementTickPointsThisHour = 0;

    /** Palier dérivé des points (non stocké en base). */
    @Transient
    @JsonProperty("platformEngagementTier")
    public String getPlatformEngagementTier() {
        return PlatformEngagementTier
                .fromTotalPoints(Optional.ofNullable(platformEngagementPoints).orElse(0))
                .name();
    }

    @Transient
    @JsonProperty("platformEngagementTierLabelFr")
    public String getPlatformEngagementTierLabelFr() {
        return PlatformEngagementTier
                .fromTotalPoints(Optional.ofNullable(platformEngagementPoints).orElse(0))
                .getLabelFr();
    }
}
