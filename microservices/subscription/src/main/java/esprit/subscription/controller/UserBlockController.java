package esprit.subscription.controller;

import esprit.subscription.DTO.liveness.*;
import esprit.subscription.entity.BlockStatus;
import esprit.subscription.entity.UserBlock;
import esprit.subscription.service.AdminReVerificationService;
import esprit.subscription.service.UserBlockService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/liveness/block", "/sub/liveness/block"})
public class UserBlockController {

    private final UserBlockService userBlockService;
    private final AdminReVerificationService adminReVerificationService;

    public UserBlockController(UserBlockService userBlockService,
                               AdminReVerificationService adminReVerificationService) {
        this.userBlockService = userBlockService;
        this.adminReVerificationService = adminReVerificationService;
    }

    @PostMapping("/request-unlock/{userId}")
    public ResponseEntity<RequestUnlockResponse> requestUnlock(@PathVariable String userId) {
        UserBlock ub = userBlockService.requestUnlock(userId);
        return ResponseEntity.ok(RequestUnlockResponse.builder()
                .status("ok")
                .message("Demande enregistrée")
                .blockId(ub.getId())
                .blockStatus(ub.getStatus())
                .build());
    }

    @GetMapping("/status/{userId}")
    public ResponseEntity<UserBlockStatusResponse> status(@PathVariable String userId) {
        return userBlockService.findByUserId(userId)
                .map(ub -> ResponseEntity.ok(UserBlockStatusResponse.builder()
                        .blocked(ub.getStatus() == BlockStatus.BLOCKED || ub.getStatus() == BlockStatus.PENDING_REVIEW)
                        .status(ub.getStatus())
                        .reason(ub.getBlockReason())
                        .blockId(ub.getId())
                        .build()))
                .orElseGet(() -> ResponseEntity.ok(UserBlockStatusResponse.builder()
                        .blocked(false)
                        .status(null)
                        .reason(null)
                        .blockId(null)
                        .build()));
    }

    @PostMapping("/verify-token")
    public ResponseEntity<VerifyTokenResponse> verifyToken(@RequestBody VerifyTokenRequest body) {
        boolean valid = adminReVerificationService.validateReVerificationToken(
                body.getToken(), body.getUserId());
        return ResponseEntity.ok(VerifyTokenResponse.builder().valid(valid).build());
    }

    @PostMapping("/reverification-complete")
    public ResponseEntity<VerifyTokenResponse> reverificationComplete(@RequestBody VerifyTokenRequest body) {
        boolean ok = adminReVerificationService.completeReVerification(body.getToken(), body.getUserId());
        return ResponseEntity.ok(VerifyTokenResponse.builder().valid(ok).build());
    }

    /**
     * Client-only permanent block (timeout, face block, etc.) when no server verify occurred.
     */
    @PostMapping("/register")
    public ResponseEntity<RequestUnlockResponse> registerClientBlock(
            @RequestBody ClientBlockRegisterRequest body,
            HttpServletRequest request) {
        if (body.getUserId() == null || body.getUserId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        UserBlock ub = userBlockService.blockUser(
                body.getUserId(),
                body.getReason() != null ? body.getReason() : "Blocage côté client",
                body.getSessionPayload());
        return ResponseEntity.ok(RequestUnlockResponse.builder()
                .status("registered")
                .message("Blocage enregistré")
                .blockId(ub.getId())
                .blockStatus(ub.getStatus())
                .build());
    }
}
