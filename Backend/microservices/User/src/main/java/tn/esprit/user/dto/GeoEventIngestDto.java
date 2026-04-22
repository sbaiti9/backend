package tn.esprit.user.dto;

import lombok.Data;

@Data
public class GeoEventIngestDto {
    private Long userId;
    /** LOGIN, PAYMENT, COURSE_ACCESS */
    private String eventType;
    /** IP client (optionnel si déjà résolu côté appelant). */
    private String clientIp;
}
