package esprit.entreprise.DTO;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewResponse {
    private Long id;
    private Long applicationId;
    private Long jobOfferId;
    private String jobTitle;

    // Candidat
    private Long candidateUserId;
    private String candidateName;
    private String candidateEmail;

    // Entretien
    private LocalDateTime interviewDate;
    private Integer durationMinutes;
    private String location;
    private String type;
    private String notes;
    private String status;
    private Boolean emailSent;

    /** True si le candidat a déjà soumis l’avis post-entretien anonyme. */
    private Boolean feedbackSubmitted;

    // Dates
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}