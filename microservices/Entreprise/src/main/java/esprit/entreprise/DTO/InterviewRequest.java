package esprit.entreprise.DTO;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewRequest {
    private Long applicationId;         // ID de la candidature
    private LocalDateTime interviewDate; // Date et heure de l'entretien
    private Integer durationMinutes;     // Durée (par défaut 30)
    private String location;             // Lieu ou lien visio
    private String type;                 // ON_SITE, VIDEO_CALL, PHONE_CALL
    private String notes;                // Notes optionnelles
}