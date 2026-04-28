package esprit.subscription.controller;

import esprit.subscription.DTO.liveness.AdminRejectBody;
import esprit.subscription.DTO.liveness.AdminUsernameBody;
import esprit.subscription.DTO.liveness.ReVerificationRequireResponse;
import esprit.subscription.config.LivenessAdminAuthHelper;
import esprit.subscription.entity.UserBlock;
import esprit.subscription.service.AdminReVerificationService;
import esprit.subscription.service.UserBlockService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping({"/api/admin/blocks", "/sub/admin/blocks"})
public class AdminUserBlockController {

    private final UserBlockService userBlockService;
    private final AdminReVerificationService adminReVerificationService;
    private final LivenessAdminAuthHelper adminAuthHelper;

    public AdminUserBlockController(
            UserBlockService userBlockService,
            AdminReVerificationService adminReVerificationService,
            LivenessAdminAuthHelper adminAuthHelper) {
        this.userBlockService = userBlockService;
        this.adminReVerificationService = adminReVerificationService;
        this.adminAuthHelper = adminAuthHelper;
    }

    @GetMapping
    public ResponseEntity<List<UserBlock>> pending(HttpServletRequest request,
                                                   @RequestParam(required = false, defaultValue = "pending") String view) {
        adminAuthHelper.requireAdminSecret(request);
        if ("all".equalsIgnoreCase(view)) {
            return ResponseEntity.ok(userBlockService.findAllBlocks());
        }
        return ResponseEntity.ok(userBlockService.findPendingReview());
    }

    @PostMapping("/{blockId}/approve")
    public ResponseEntity<UserBlock> approve(
            @PathVariable Long blockId,
            @RequestBody(required = false) AdminUsernameBody body,
            HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        String admin = body != null && body.getAdminUsername() != null ? body.getAdminUsername() : "admin";
        return ResponseEntity.ok(userBlockService.approveUnlock(blockId, admin));
    }

    @PostMapping("/{blockId}/reject")
    public ResponseEntity<UserBlock> reject(
            @PathVariable Long blockId,
            @RequestBody AdminRejectBody body,
            HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        String admin = body != null && body.getAdminUsername() != null ? body.getAdminUsername() : "admin";
        String reason = body != null ? body.getReason() : "";
        return ResponseEntity.ok(userBlockService.rejectUnlock(blockId, admin, reason));
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<List<UserBlock>> history(@PathVariable String userId, HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        return ResponseEntity.ok(userBlockService.historyForUser(userId));
    }

    @PostMapping("/{blockId}/require-reverification")
    public ResponseEntity<ReVerificationRequireResponse> requireReverification(
            @PathVariable Long blockId,
            @RequestBody(required = false) AdminUsernameBody body,
            HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        String admin = body != null && body.getAdminUsername() != null ? body.getAdminUsername() : "admin";
        AdminReVerificationService.ReVerificationTokenResult r =
                adminReVerificationService.requireReVerification(blockId, admin);
        return ResponseEntity.ok(ReVerificationRequireResponse.builder()
                .token(r.token())
                .expiresAt(r.expiresAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build());
    }
}
