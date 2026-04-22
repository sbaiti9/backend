package esprit.entreprise.DTO;

import lombok.*;
import java.time.LocalDateTime;

/**
 * DTO pour afficher les détails d'une candidature avec infos du candidat
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobApplicationResponse {
    private Long id;
    private Long userId;
    private Long jobOfferId;
    private String jobTitle;
    private String companyName;

    // Infos du candidat
    private String candidateName;
    private String candidateEmail;
    private String educationLevel;
    private String candidateStatus;
    private Double averageRating;
    private String profession;

    // Scores
    private Integer matchingScore;
    private Double educationLevelScore;
    private Double ratingScore;
    private Double statusScore;

    // Status
    private String applicationStatus;
    private String coverLetter;
    private String cvFileName;
    private Boolean hasCv;
    private LocalDateTime applicationDate;
    private LocalDateTime updatedDate;
}