package esprit.entreprise.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "companies")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String industry;

    @Column(nullable = false)
    private String location;

    private String website;

    private String logoUrl;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    // ⭐ NEW FIELD: Link to Users from User microservice
    @Column(nullable = false)
    private Long userId;

    /** Optional — used for birthday promo eligibility (subscription module). */
    private LocalDate dateOfBirth;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

}