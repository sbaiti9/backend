package tn.esprit.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "geo_auth_event", indexes = {
        @Index(name = "idx_geo_user_created", columnList = "user_id,created_at"),
        @Index(name = "idx_geo_impossible", columnList = "impossible_travel,created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeoAuthEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GeoEventType eventType;

    @Column(length = 64)
    private String ipAddress;

    private Double latitude;
    private Double longitude;

    @Column(length = 128)
    private String city;

    @Column(length = 128)
    private String country;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private boolean impossibleTravel;

    /** Vitesse impliquée (km/h) entre cet événement et le précédent géolocalisé. */
    private Double impliedSpeedKmh;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
