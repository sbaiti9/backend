package tn.esprit.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.user.entity.Users;
import tn.esprit.user.repository.UserRepository;
import tn.esprit.user.service.AuthRiskAssessmentService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user/admin/security")
public class AdminAccountSecurityController {

    private final UserRepository userRepository;
    private final AuthRiskAssessmentService authRiskAssessmentService;

    public AdminAccountSecurityController(UserRepository userRepository,
                                          AuthRiskAssessmentService authRiskAssessmentService) {
        this.userRepository = userRepository;
        this.authRiskAssessmentService = authRiskAssessmentService;
    }

    /**
     * Unlock a temporarily locked account after "AI" verification (geo-security anomaly gate).
     * If risk is detected, unlock is refused unless force=true.
     */
    @PostMapping("/unlock/{userId}")
    public ResponseEntity<?> unlock(@PathVariable Long userId,
                                    @RequestParam(name = "force", defaultValue = "false") boolean force) {
        Users u = userRepository.findById(userId).orElse(null);
        if (u == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }

        AuthRiskAssessmentService.RiskDecision decision = authRiskAssessmentService.assessUnlockRisk(userId);
        if (!decision.allowUnlock() && !force) {
            Map<String, Object> body = new HashMap<>();
            body.put("error", "Déblocage refusé");
            body.put("reason", decision.reason());
            body.put(
                    "userMessage",
                    "Le déblocage a été refusé par l’analyse de risque : " + decision.reason()
                            + " En cas de faux positif après vérification manuelle, un administrateur peut utiliser le paramètre ?force=true.");
            body.put("hint", "Après vérification manuelle (compte légitime), appeler unlock avec ?force=true.");
            return ResponseEntity.status(409).body(body);
        }

        u.setFailedLoginAttempts(0);
        u.setLockUntil(null);
        userRepository.save(u);

        Map<String, Object> ok = new HashMap<>();
        ok.put("message", "Compte débloqué.");
        ok.put("aiAllowUnlock", decision.allowUnlock());
        ok.put("aiReason", decision.reason());
        ok.put("forced", force);
        return ResponseEntity.ok(ok);
    }
}

