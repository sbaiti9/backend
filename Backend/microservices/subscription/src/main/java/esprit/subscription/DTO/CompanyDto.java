package esprit.subscription.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompanyDto {
    private Long id;
    private String email;
    private String name;
    private Long userId;
    private LocalDateTime createdAt;
    private LocalDate dateOfBirth;
}
