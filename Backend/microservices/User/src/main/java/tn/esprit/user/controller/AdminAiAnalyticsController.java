package tn.esprit.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.esprit.user.dto.ChurnBatchReport;
import tn.esprit.user.dto.UserActivityAiReport;
import tn.esprit.user.service.UserActivityAiService;
import tn.esprit.user.service.UserChurnPredictionService;

@RestController
@RequestMapping("/user/admin/ai")
public class AdminAiAnalyticsController {

    private final UserActivityAiService userActivityAiService;
    private final UserChurnPredictionService userChurnPredictionService;

    public AdminAiAnalyticsController(
            UserActivityAiService userActivityAiService,
            UserChurnPredictionService userChurnPredictionService) {
        this.userActivityAiService = userActivityAiService;
        this.userChurnPredictionService = userChurnPredictionService;
    }

    /**
     * Login activity analytics for admin (based on GeoAuthEvent LOGIN events).
     * Example: GET /user/admin/ai/login-activity?days=30
     */
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("/login-activity")
    public ResponseEntity<UserActivityAiReport> loginActivity(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(userActivityAiService.buildLoginActivityReport(days));
    }

    /**
     * Prédiction de désengagement (churn) par utilisateur — heuristique + Groq optionnel.
     * Ex. GET /user/admin/ai/churn-predictions?days=30
     */
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("/churn-predictions")
    public ResponseEntity<ChurnBatchReport> churnPredictions(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(userChurnPredictionService.buildReport(days));
    }
}

