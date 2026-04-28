package tn.esprit.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.esprit.user.entity.GeoEventType;
import tn.esprit.user.entity.Users;
import tn.esprit.user.repository.UserRepository;
import tn.esprit.user.service.EngagementTickService;
import tn.esprit.user.service.GeoSecurityService;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ping géo pour accès formation / activité (utilisateur authentifié).
 */
@RestController
@RequestMapping("/user/users/me")
@RequiredArgsConstructor
public class UserGeoPingController {

    private final UserRepository userRepository;
    private final GeoSecurityService geoSecurityService;
    private final EngagementTickService engagementTickService;

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/geo-ping")
    public ResponseEntity<Void> geoPing(
            @RequestBody(required = false) Map<String, String> body,
            Principal principal,
            HttpServletRequest request) {
        Users u = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        GeoEventType type = GeoEventType.COURSE_ACCESS;
        if (body != null && body.containsKey("eventType")) {
            try {
                type = GeoEventType.valueOf(body.get("eventType").trim());
            } catch (Exception ignored) {
                type = GeoEventType.COURSE_ACCESS;
            }
        }
        geoSecurityService.recordEvent(u.getId(), type, request);
        return ResponseEntity.ok().build();
    }

    /**
     * Appel périodique (ex. toutes les 10 s) pendant une session authentifiée : +points si l’intervalle est respecté.
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/engagement-tick")
    public ResponseEntity<Map<String, Object>> engagementTick(Principal principal) {
        Users u = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        EngagementTickService.TickResult r = engagementTickService.tryAwardTick(u.getId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("awarded", r.awarded());
        body.put("pointsAdded", r.pointsAdded());
        body.put("totalPoints", r.totalPoints());
        body.put("tier", r.tier());
        body.put("tierLabelFr", r.tierLabelFr());
        body.put("waitSeconds", r.waitSeconds());
        body.put("messageFr", r.messageFr());
        return ResponseEntity.ok(body);
    }
}
