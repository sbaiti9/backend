package esprit.entreprise.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Avis anonyme candidat après entretien (agrégé côté entreprise / admin, jamais renvoyé nominativement à l’employeur).
 */
@Entity
@Table(
        name = "interview_candidate_feedback",
        uniqueConstraints = @UniqueConstraint(name = "uk_interview_feedback_interview", columnNames = "interview_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewCandidateFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interview_id", nullable = false, unique = true)
    private Long interviewId;

    @Column(name = "job_offer_id", nullable = false)
    private Long jobOfferId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Conservé pour dédoublonnage / audit ; non exposé aux API entreprise. */
    @Column(name = "candidate_user_id", nullable = false)
    private Long candidateUserId;

    /** 1–5 */
    @Column(nullable = false)
    private int overallRating;

    @Column(name = "professionalism_rating")
    private Integer professionalismRating;

    @Column(name = "punctuality_rating")
    private Integer punctualityRating;

    @Column(name = "clarity_rating")
    private Integer clarityRating;

    @Column(length = 400)
    private String comment;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
