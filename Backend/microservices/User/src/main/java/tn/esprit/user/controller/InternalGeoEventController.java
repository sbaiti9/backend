package tn.esprit.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import tn.esprit.user.dto.GeoEventIngestDto;
import tn.esprit.user.entity.GeoEventType;
import tn.esprit.user.service.GeoSecurityService;

/**
 * Ingestion inter-services (paiement, etc.) — protégée par en-tête {@code X-Internal-Key}.
 */
@RestController
@RequestMapping("/user/internal")
@RequiredArgsConstructor
public class InternalGeoEventController {

    private final GeoSecurityService geoSecurityService;

    @Value("${skillio.internal.api-key:}")
    private String internalApiKey;

    @PostMapping("/geo-event")
    public ResponseEntity<Void> ingest(
            @RequestHeader(value = "X-Internal-Key", required = false) String key,
            @RequestBody GeoEventIngestDto body) {
        if (!StringUtils.hasText(internalApiKey) || !internalApiKey.equals(key)) {
            return ResponseEntity.status(403).build();
        }
        if (body == null || body.getUserId() == null) {
            return ResponseEntity.badRequest().build();
        }
        GeoEventType type;
        try {
            type = GeoEventType.valueOf(body.getEventType() != null ? body.getEventType().trim() : "PAYMENT");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        String ip = body.getClientIp();
        if (!StringUtils.hasText(ip)) {
            return ResponseEntity.badRequest().build();
        }
        geoSecurityService.recordEvent(body.getUserId(), type, ip.trim());
        return ResponseEntity.ok().build();
    }
}
