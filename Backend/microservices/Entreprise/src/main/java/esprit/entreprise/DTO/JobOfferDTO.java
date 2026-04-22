package esprit.entreprise.DTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JobOfferDTO {
    private String title;
    private String description;
    private String contractType;
    private String location;
    private Double salary;
    private String remote;
    private String requirements;
    private Boolean isActive;
    private Long companyId; // juste l'ID de la company
}