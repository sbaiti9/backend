package esprit.entreprise.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Référence à l'utilisateur (depuis User MS - userId)
    private Long userId;

    // Relation avec l'offre d'emploi (même MS)
    @ManyToOne
    @JoinColumn(name = "job_offer_id", nullable = false)
    private JobOffer jobOffer;

    // Statut de la candidature
    @Enumerated(EnumType.STRING)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    // Score de matching (0-100)
    private Integer matchingScore;

    // Détails du calcul de matching
    private Double educationLevelScore = 0.0;      // Score basé sur le niveau d'étude (Bac, Bac+2, Bac+3)
    private Double ratingScore = 0.0;               // Score basé sur la notation moyenne (0-5 => 0-100)
    private Double statusScore = 0.0;               // Score basé sur le statut (ACTIVE, EN_ATTENTE, NON_ACTIVE)

    // Dates
    private LocalDateTime applicationDate;
    private LocalDateTime updatedDate;

    // Message de candidature optionnel
    @Column(columnDefinition = "TEXT")
    private String coverLetter;

    // CV upload (optionnel)
    private String cvFileName;
    private String cvContentType;

    @Lob
    @Column(name = "cv_data", columnDefinition = "LONGBLOB")
    private byte[] cvData;

    // Informations du candidat (sauvegardées à la candidature pour historique)
    private String candidateName;
    private String candidateEmail;
    private String educationLevel;              // "Bac", "Bac_plus_2", "Bac_plus_3", "Autre"
    private String candidateStatus;             // "ACTIVE", "EN_ATTENTE", "NON_ACTIVE"
    private Double candidateAverageRating;      // 0.0 à 5.0

    // Poids pour le calcul du matching (40% éducation, 30% rating, 30% statut)
    @Transient
    private static final Double EDUCATION_WEIGHT = 0.40;
    @Transient
    private static final Double RATING_WEIGHT = 0.30;
    @Transient
    private static final Double STATUS_WEIGHT = 0.30;

    /**
     * Calcule le score de matching total
     * Formule: (educationScore * 0.40) + (ratingScore * 0.30) + (statusScore * 0.30)
     */
    public void calculateMatchingScore() {
        if (this.educationLevelScore != null && this.ratingScore != null && this.statusScore != null) {
            // ✅ CORRECTION: Math.round() retourne un long, pas un int
            double calculatedScore = educationLevelScore * EDUCATION_WEIGHT +
                    ratingScore * RATING_WEIGHT +
                    statusScore * STATUS_WEIGHT;

            this.matchingScore = (int) Math.round(calculatedScore);

            // Limiter entre 0 et 100
            if (this.matchingScore > 100) {
                this.matchingScore = 100;
            }
            if (this.matchingScore < 0) {
                this.matchingScore = 0;
            }
        }
    }

    @PrePersist
    protected void onCreate() {
        this.applicationDate = LocalDateTime.now();
        this.updatedDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedDate = LocalDateTime.now();
    }
}