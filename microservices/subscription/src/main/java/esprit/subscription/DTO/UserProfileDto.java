package esprit.subscription.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Subset of user-service {@code Users} JSON for plan recommendations (Feign).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@lombok.Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfileDto {
    private Long id;
    private String name;
    private String prenom;
    private String email;
    /** Serialized role enum, e.g. ETUDIANT, ENTREPRISE */
    private String role;
    private String profession;
    private String specialte;
    private String educationLevel;
    @JsonProperty("average_rating")
    private Double averageRating;
}
