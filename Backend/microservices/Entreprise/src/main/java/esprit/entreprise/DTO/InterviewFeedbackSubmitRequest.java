package esprit.entreprise.DTO;

import lombok.Data;

/**
 * Corps POST pour soumettre un avis post-entretien (candidat).
 */
@Data
public class InterviewFeedbackSubmitRequest {

    private Long interviewId;
    /** Doit correspondre au candidat de l’entretien (même contrôle que le reste du MS). */
    private Long candidateUserId;

    /** 1–5 obligatoire */
    private Integer overallRating;

    private Integer professionalismRating;
    private Integer punctualityRating;
    private Integer clarityRating;

    /** Optionnel, max 400 car. — visible admin uniquement côté API stats détaillées si exposé plus tard. */
    private String comment;
}
