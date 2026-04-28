package tn.esprit.user.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.user.config.LivenessAdminAuthHelper;
import tn.esprit.user.entity.Users;
import tn.esprit.user.service.GeoSecurityService;

import java.util.Map;

@RestController
@RequestMapping("/user/internal/geo-security")
@RequiredArgsConstructor
public class GeoSecurityInternalController {

    private final GeoSecurityService geoSecurityService;
    private final LivenessAdminAuthHelper livenessAdminAuthHelper;

    /**
     * JWT-independent hard-freeze path under /user/internal/** (already permitAll in security config).
     */
    @PostMapping("/users/{userId}/hard-freeze")
    public ResponseEntity<?> hardFreezeNoJwt(@PathVariable Long userId, HttpServletRequest request) {
        try {
            livenessAdminAuthHelper.requireAdminSecret(request);
            Users u = geoSecurityService.freezeAndInvalidateTokens(userId);
            return ResponseEntity.ok(Map.of(
                    "userId", u.getId(),
                    "status", u.getStatus().name(),
                    "tokenVersion", u.getTokenVersion(),
                    "mode", "hard-freeze-no-jwt-internal"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
