package esprit.subscription.controller;

import esprit.subscription.DTO.unlockchat.*;
import esprit.subscription.config.LivenessAdminAuthHelper;

import esprit.subscription.entity.UnlockChatMessage;
import esprit.subscription.entity.UnlockChatSession;
import esprit.subscription.service.SseStreamService;
import esprit.subscription.service.UnlockChatAiAnalysisService;
import esprit.subscription.service.UnlockChatService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Manual admin–user unlock chat. Do not add {@code @CrossOrigin}: the gateway sets CORS.
 */
@RestController
@RequestMapping({"/api/unlock-chat", "/sub/unlock-chat"})
public class UnlockChatController {

    private static final Logger log = LoggerFactory.getLogger(UnlockChatController.class);

    private final UnlockChatService unlockChatService;
    private final SseStreamService sseStreamService;
    private final LivenessAdminAuthHelper adminAuthHelper;
    private final UnlockChatAiAnalysisService unlockChatAiAnalysisService;

    public UnlockChatController(
            UnlockChatService unlockChatService,
            SseStreamService sseStreamService,
            LivenessAdminAuthHelper adminAuthHelper,
            UnlockChatAiAnalysisService unlockChatAiAnalysisService) {
        this.unlockChatService = unlockChatService;
        this.sseStreamService = sseStreamService;
        this.adminAuthHelper = adminAuthHelper;
        this.unlockChatAiAnalysisService = unlockChatAiAnalysisService;
    }

    @PostConstruct
    void logMappings() {
        log.info("Unlock-chat API ready: GET /api/unlock-chat/ping — if you see 404 on this URL, restart subscription with a freshly built JAR (mvnw package).");
    }

    /** Sanity check: GET must return 200 after deploy. A 404 here means an outdated subscription process is still running. */
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("ok", true, "service", "unlock-chat");
    }

    @PostMapping("/start")
    public ResponseEntity<UnlockChatSessionDTO> start(@RequestBody UnlockChatStartRequest body) {
        UnlockChatSession session = unlockChatService.openSession(
                body.getUserId(), body.getBlockId(), body.getMessage());
        return ResponseEntity.ok(unlockChatService.getSession(session.getId()));
    }

    @PostMapping("/message")
    public ResponseEntity<UnlockChatMessageDTO> userMessage(@RequestBody UserMessageRequest body) {
        UnlockChatMessage m = unlockChatService.userSendMessage(
                body.getSessionId(), body.getUserId(), body.getContent());
        return ResponseEntity.ok(unlockChatService.toMessageDto(m));
    }

    /**
     * No resumable session → 204 (avoids noisy 404 in browsers for “no chat yet”).
     */
    @GetMapping("/session/{userId}")
    public ResponseEntity<UnlockChatSessionDTO> userSession(@PathVariable String userId) {
        return unlockChatService.getResumeStateForUser(userId)
                .map(dto -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(dto))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT)
                        .cacheControl(CacheControl.noStore())
                        .build());
    }

    @GetMapping("/session/{userId}/open")
    public ResponseEntity<UnlockChatSessionDTO> userOpenSessionOnly(@PathVariable String userId) {
        return unlockChatService.getOpenSessionForUser(userId)
                .map(dto -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(dto))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/user/stream/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter userStream(@PathVariable String userId) {
        return sseStreamService.subscribeUser(userId);
    }

    @GetMapping("/admin/sessions")
    public ResponseEntity<java.util.List<UnlockChatSessionSummaryDTO>> adminSessions(HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        return ResponseEntity.ok(unlockChatService.getOpenSessions());
    }

    @GetMapping("/admin/sessions/{sessionId}")
    public ResponseEntity<UnlockChatSessionDTO> adminSession(
            @PathVariable Long sessionId,
            HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        return ResponseEntity.ok(unlockChatService.getSession(sessionId));
    }

    @PostMapping("/admin/message")
    public ResponseEntity<UnlockChatMessageDTO> adminMessage(
            @RequestBody AdminMessageRequest body,
            HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        UnlockChatMessage m = unlockChatService.adminSendMessage(
                body.getSessionId(),
                body.getAdminUsername() != null ? body.getAdminUsername() : "admin",
                body.getContent());
        return ResponseEntity.ok(unlockChatService.toMessageDto(m));
    }

    @PostMapping("/admin/sessions/{sessionId}/grant")
    public ResponseEntity<Map<String, Boolean>> grant(
            @PathVariable Long sessionId,
            @RequestBody(required = false) AdminUsernameBody body,
            HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        String admin = body != null && body.getAdminUsername() != null ? body.getAdminUsername() : "admin";
        unlockChatService.grantAccess(sessionId, admin);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Même effet que {@link #grant} mais {@code sessionId} dans le corps JSON — préféré par le front si un proxy
     * ou la gateway pose problème sur les chemins avec plusieurs segments.
     */
    @PostMapping("/admin/grant-access")
    public ResponseEntity<Map<String, Boolean>> grantByBody(
            @RequestBody GrantAccessBody body,
            HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        if (body == null || body.getSessionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId required");
        }
        String admin = body.getAdminUsername() != null ? body.getAdminUsername() : "admin";
        unlockChatService.grantAccess(body.getSessionId(), admin);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/admin/sessions/{sessionId}/reject")
    public ResponseEntity<Map<String, Boolean>> reject(
            @PathVariable Long sessionId,
            @RequestBody AdminRejectBody body,
            HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        String admin = body.getAdminUsername() != null ? body.getAdminUsername() : "admin";
        String reason = body.getReason() != null ? body.getReason() : "";
        unlockChatService.rejectAccess(sessionId, admin, reason);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** Re-run Groq fraud analysis for this session (async). */
    @PostMapping("/admin/sessions/{sessionId}/analyze-ai")
    public ResponseEntity<Map<String, String>> analyzeAi(
            @PathVariable Long sessionId,
            HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        unlockChatAiAnalysisService.scheduleForcedAnalysis(sessionId);
        return ResponseEntity.accepted().body(Map.of("status", "scheduled"));
    }

    /**
     * Same as {@link #analyzeAi} but session id in JSON body — use this if the path-variable URL 404s behind a proxy.
     */
    @PostMapping("/admin/analyze-ai")
    public ResponseEntity<Map<String, String>> analyzeAiByBody(
            @RequestBody AnalyzeAiRequest body,
            HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        if (body == null || body.getSessionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId required");
        }
        unlockChatAiAnalysisService.scheduleForcedAnalysis(body.getSessionId());
        return ResponseEntity.accepted().body(Map.of("status", "scheduled"));
    }

    @GetMapping(value = "/admin/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter adminStream(HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        return sseStreamService.subscribeAdmin();
    }

    /** SSE dédié aux alertes admin (séparé du flux unlock-chat inbox). */
    @GetMapping(value = "/admin/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter adminNotificationsStream(HttpServletRequest request) {
        adminAuthHelper.requireAdminSecret(request);
        return sseStreamService.subscribeAdminNotifications();
    }
}
