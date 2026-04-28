package esprit.entreprise.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Message entre l’administration Skillio et une entreprise (fil de discussion par {@code companyId}).
 */
@Entity
@Table(name = "support_messages")
@Getter
@Setter
@NoArgsConstructor
public class SupportMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long companyId;

    /** ADMIN ou ENTREPRISE */
    @Column(nullable = false, length = 32)
    private String senderRole;

    /** Identifiant compte utilisateur (table users côté module User). */
    @Column(nullable = false)
    private Long senderUserId;

    @Column(nullable = false, length = 4000)
    private String body;

    /**
     * Pour les messages {@code ENTREPRISE} : lu par l’admin ou non (badge notifications).
     * Pour les messages {@code ADMIN} : toujours {@code true} (ne comptent pas comme « à traiter »).
     */
    @Column(nullable = true)
    private Boolean readByAdmin;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
