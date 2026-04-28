package esprit.subscription.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "intervention_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterventionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String actionType;

    private LocalDateTime recommendedAt;

    private LocalDateTime executedAt;

    /** SUCCESS, MISS, PENDING */
    @Column(nullable = false, length = 16)
    private String outcome;

    private Integer confidenceAtTime;

    @PrePersist
    void prePersist() {
        if (recommendedAt == null) {
            recommendedAt = LocalDateTime.now();
        }
    }
}
