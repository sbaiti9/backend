package esprit.entreprise.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDTO {
    private String name;
    private String industry;
    private String location;
    private String website;
    private String logoUrl;
    private String email;
    private String phone;

    // ⭐ NEW: userId is required - link to Users from User microservice
    private Long userId;
}