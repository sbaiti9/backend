package esprit.entreprise.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_offers")

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class JobOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private String contractType; // CDI, CDD, INTERNSHIP, FREELANCE

    @Column(nullable = false)
    private String location;

    private Double salary;

    @Column(nullable = false)
    private String remote; // ON_SITE, HYBRID, FULL_REMOTE

    @Column(length = 2000)
    private String requirements;



    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private Boolean isActive;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
    }
    @ManyToOne
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

}
