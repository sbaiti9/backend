package esprit.entreprise.DTO;

import lombok.*;

/**
 * DTO pour les informations utilisateur récupérées via Feign Client
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInfoDTO {
    private Long id;
    private String name;
    private String email;
    private String prenom;
    private String educationLevel;      // "Bac", "Bac_plus_2", "Bac_plus_3", "Autre"
    private String status;              // "ACTIVE", "EN_ATTENTE", "NON_ACTIVE"
    private Double averageRating;       // 0.0 à 5.0
    private String profession;
    private String specialite;
}