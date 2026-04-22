package esprit.subscription.controller;

import esprit.subscription.DTO.LivenessChallengeResponse;
import esprit.subscription.DTO.LivenessVerifyRequest;
import esprit.subscription.DTO.LivenessVerifyResponse;
import esprit.subscription.service.LivenessService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/liveness", "/sub/liveness"})
public class LivenessController {

    private final LivenessService livenessService;

    public LivenessController(LivenessService livenessService) {
        this.livenessService = livenessService;
    }

    @GetMapping("/challenge")
    public ResponseEntity<LivenessChallengeResponse> challenge(
            @RequestParam Long userId,
            HttpServletRequest request) {
        return ResponseEntity.ok(livenessService.createChallenge(userId, request));
    }

    @PostMapping("/verify")
    public ResponseEntity<LivenessVerifyResponse> verify(
            @RequestBody LivenessVerifyRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(livenessService.verify(body, request));
    }
}
