package esprit.entreprise.DTO;

import lombok.*;

/**
 * DTO pour créer une candidature
 * Le backend récupère les infos utilisateur via Feign Client
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobApplicationRequest {
    private Long userId;                // ID de l'utilisateur
    private Long jobOfferId;            // ID de l'offre
    private String coverLetter;         // Message optionnel
}