package esprit.entreprise.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "interviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Interview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Lien vers la candidature
    @ManyToOne
    @JoinColumn(name = "application_id", nullable = false)
    private JobApplication application;

    // Lien vers l'offre d'emploi
    @ManyToOne
    @JoinColumn(name = "job_offer_id", nullable = false)
    private JobOffer jobOffer;

    // Informations du candidat (copiées pour faciliter l'affichage)
    private Long candidateUserId;
    private String candidateName;
    private String candidateEmail;

    // Date et heure de l'entretien
    @Column(nullable = false)
    private LocalDateTime interviewDate;

    // Durée en minutes (par défaut 30)
    private Integer durationMinutes = 30;

    // Lieu ou lien de l'entretien
    private String location;

    // Type d'entretien
    @Enumerated(EnumType.STRING)
    private InterviewType type = InterviewType.ON_SITE;

    // Notes / commentaires de l'entreprise
    @Column(columnDefinition = "TEXT")
    private String notes;

    // Statut de l'entretien
    @Enumerated(EnumType.STRING)
    private InterviewStatus status = InterviewStatus.SCHEDULED;

    // Email envoyé ?
    private Boolean emailSent = false;

    // Dates de création et mise à jour
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Enums ──

    public enum InterviewType {
        ON_SITE,        // Présentiel
        VIDEO_CALL,     // Visioconférence
        PHONE_CALL      // Téléphone
    }

    public enum InterviewStatus {
        SCHEDULED,      // Planifié
        COMPLETED,      // Terminé
        CANCELLED,      // Annulé
        NO_SHOW         // Candidat absent
    }
}