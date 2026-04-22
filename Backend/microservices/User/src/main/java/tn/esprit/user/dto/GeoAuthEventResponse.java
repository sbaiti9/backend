package tn.esprit.user.dto;

import lombok.Builder;
import lombok.Data;
import tn.esprit.user.entity.GeoAuthEvent;

@Data
@Builder
public class GeoAuthEventResponse {
    private Long id;
    private Long userId;
    private String eventType;
    private String ipAddress;
    private Double latitude;
    private Double longitude;
    private String city;
    private String country;
    private String createdAt;
    private boolean impossibleTravel;
    private Double impliedSpeedKmh;

    public static GeoAuthEventResponse from(GeoAuthEvent e) {
        return GeoAuthEventResponse.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .eventType(e.getEventType() != null ? e.getEventType().name() : null)
                .ipAddress(e.getIpAddress())
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .city(e.getCity())
                .country(e.getCountry())
                .createdAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null)
                .impossibleTravel(e.isImpossibleTravel())
                .impliedSpeedKmh(e.getImpliedSpeedKmh())
                .build();
    }
}
