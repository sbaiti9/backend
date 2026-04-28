package esprit.subscription.service;

import esprit.subscription.DTO.LivenessChallengeResponse;
import esprit.subscription.DTO.LivenessVerifyRequest;
import esprit.subscription.DTO.LivenessVerifyResponse;
import esprit.subscription.entity.BlockedPaymentAttempt;
import esprit.subscription.entity.ChallengeGesture;
import esprit.subscription.entity.LivenessVerification;
import esprit.subscription.entity.LivenessVerificationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import esprit.subscription.repository.BlockedPaymentAttemptRepository;
import esprit.subscription.repository.LivenessVerificationRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class LivenessService {

    private final int challengeWindowMinutes;
    private final int maxChallengesPerWindow;
    private static final int BLOCK_MINUTES = 30;
    private static final double PASS_THRESHOLD = 80.0;

    private final LivenessVerificationRepository verificationRepository;
    private final BlockedPaymentAttemptRepository blockedRepository;
    private final LivenessJwtService livenessJwtService;
    private final UserBlockService userBlockService;
    private final ObjectMapper objectMapper;

    public LivenessService(
            LivenessVerificationRepository verificationRepository,
            BlockedPaymentAttemptRepository blockedRepository,
            LivenessJwtService livenessJwtService,
            UserBlockService userBlockService,
            ObjectMapper objectMapper,
            @Value("${liveness.challenge.window-minutes:10}") int challengeWindowMinutes,
            @Value("${liveness.challenge.max-per-window:3}") int maxChallengesPerWindow) {
        this.verificationRepository = verificationRepository;
        this.blockedRepository = blockedRepository;
        this.livenessJwtService = livenessJwtService;
        this.userBlockService = userBlockService;
        this.objectMapper = objectMapper;
        this.challengeWindowMinutes = Math.max(1, challengeWindowMinutes);
        this.maxChallengesPerWindow = Math.max(1, maxChallengesPerWindow);
    }

    /**
     * Validates the liveness JWT and marks the verification row as consumed (one payment per token).
     */
    @Transactional
    public void assertAndConsumePaymentJwt(String jwt, long userId) {
        if (jwt == null || jwt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vérification d'identité requise");
        }
        String t = jwt.trim();
        if (!livenessJwtService.isValidForPayment(t, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vérification d'identité requise");
        }
        Claims claims = livenessJwtService.parseAndValidate(t);
        if (claims == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vérification d'identité requise");
        }
        String challengeTok = claims.get("challenge", String.class);
        if (challengeTok == null || challengeTok.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vérification d'identité requise");
        }
        LivenessVerification v = verificationRepository.findByChallengeToken(challengeTok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Vérification d'identité requise"));
        if (v.getJwtConsumedAt() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Jeton liveness déjà utilisé");
        }
        if (v.getStatus() != LivenessVerificationStatus.PASSED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vérification d'identité requise");
        }
        v.setJwtConsumedAt(LocalDateTime.now());
        verificationRepository.save(v);
    }

    @Transactional(readOnly = true)
    public void assertNotBlocked(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        blockedRepository.findFirstByUserIdAndBlockedUntilAfterOrderByBlockedUntilDesc(userId, now)
                .ifPresent(b -> {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Payment temporarily blocked after failed liveness attempts");
                });
    }

    @Transactional
    public LivenessChallengeResponse createChallenge(Long userId, HttpServletRequest request) {
        assertNotBlocked(userId);
        if (userBlockService.isLivenessForbidden(String.valueOf(userId))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Compte bloqué — demandez un déblocage ou contactez le support.");
        }

        LocalDateTime since = LocalDateTime.now().minusMinutes(challengeWindowMinutes);
        int recent = verificationRepository.countByUserIdAndCreatedAtAfter(userId, since);
        if (recent >= maxChallengesPerWindow) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de demandes de défi de présence — réessayez dans quelques minutes (limite anti-abus).");
        }

        ChallengeGesture gesture = ChallengeGesture.random();
        LivenessVerification v = new LivenessVerification();
        v.setUserId(userId);
        v.setChallengeGesture(gesture);
        v.setChallengeToken(UUID.randomUUID().toString());
        v.setStatus(LivenessVerificationStatus.PENDING);
        v.setAttemptCount(0);
        v.setCreatedAt(LocalDateTime.now());
        v.setExpiresAt(v.getCreatedAt().plusMinutes(3));
        v.setIpAddress(clientIp(request));
        String ua = request.getHeader("User-Agent");
        v.setUserAgent(ua != null && ua.length() > 500 ? ua.substring(0, 500) : ua);

        verificationRepository.save(v);

        return LivenessChallengeResponse.builder()
                .challengeToken(v.getChallengeToken())
                .gesture(gesture.name())
                .gestureLabel(gesture.frenchLabel())
                .expiresAt(v.getExpiresAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
    }

    @Transactional
    public LivenessVerifyResponse verify(LivenessVerifyRequest req, HttpServletRequest request) {
        if (req.getChallengeToken() == null || req.getChallengeToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "challengeToken required");
        }

        LivenessVerification v = verificationRepository.findByChallengeToken(req.getChallengeToken().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown challenge"));

        if (userBlockService.isLivenessForbidden(String.valueOf(v.getUserId()))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Compte bloqué — demandez un déblocage ou contactez le support.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (v.getStatus() == LivenessVerificationStatus.EXPIRED
                || now.isAfter(v.getExpiresAt())) {
            if (v.getStatus() == LivenessVerificationStatus.PENDING) {
                v.setStatus(LivenessVerificationStatus.EXPIRED);
                verificationRepository.save(v);
            }
            throw new ResponseStatusException(HttpStatus.GONE, "Challenge expired");
        }

        if (v.getStatus() != LivenessVerificationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Challenge already used");
        }

        if (!req.isFaceValidated()) {
            registerUserBlockFailure(v.getUserId(), "Visage humain non confirmé", req);
            return LivenessVerifyResponse.builder()
                    .passed(false)
                    .globalScore(0)
                    .message("Visage humain non confirmé")
                    .build();
        }
        if (req.getBlinkCount() > 5) {
            registerUserBlockFailure(v.getUserId(), "Trop de clignements détectés", req);
            return LivenessVerifyResponse.builder()
                    .passed(false)
                    .globalScore(0)
                    .message("Trop de clignements détectés")
                    .build();
        }
        if (req.getTimeUsedMs() < 4000) {
            registerUserBlockFailure(v.getUserId(), "Session complétée trop rapidement", req);
            return LivenessVerifyResponse.builder()
                    .passed(false)
                    .globalScore(0)
                    .message("Session complétée trop rapidement")
                    .build();
        }
        if (req.getTimeUsedMs() > 62000) {
            registerUserBlockFailure(v.getUserId(), "Session expirée", req);
            return LivenessVerifyResponse.builder()
                    .passed(false)
                    .globalScore(0)
                    .message("Session expirée")
                    .build();
        }

        double l = clampScore(req.getLivenessScore());
        double g = clampScore(req.getGestureScore());
        double a = clampScore(req.getAntiSpoofScore());
        double global = l * 0.35 + g * 0.40 + a * 0.25;

        v.setLivenessScore(l);
        v.setGestureScore(g);
        v.setAntiSpoofScore(a);
        v.setGlobalScore(global);

        LivenessVerifyResponse.LivenessVerifyResponseBuilder b = LivenessVerifyResponse.builder()
                .globalScore(global);

        if (global >= PASS_THRESHOLD) {
            v.setStatus(LivenessVerificationStatus.PASSED);
            verificationRepository.save(v);
            String jwt = livenessJwtService.createToken(v.getUserId(), v.getChallengeToken());
            return b.passed(true).jwtToken(jwt).build();
        }

        registerUserBlockFailure(v.getUserId(), "Score global insuffisant", req);

        v.setAttemptCount(v.getAttemptCount() + 1);
        String blockedUntilIso = null;
        if (v.getAttemptCount() >= 3) {
            v.setStatus(LivenessVerificationStatus.FAILED);
            BlockedPaymentAttempt block = new BlockedPaymentAttempt();
            block.setUserId(v.getUserId());
            block.setIpAddress(clientIp(request));
            LocalDateTime until = now.plusMinutes(BLOCK_MINUTES);
            block.setBlockedUntil(until);
            block.setReason("Liveness verification failed 3 times");
            blockedRepository.save(block);
            blockedUntilIso = until.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        verificationRepository.save(v);

        return b.passed(false).blockedUntil(blockedUntilIso).message("Score insuffisant").build();
    }

    private void registerUserBlockFailure(Long userId, String message, LivenessVerifyRequest req) {
        try {
            userBlockService.blockUser(String.valueOf(userId), message, objectMapper.writeValueAsString(req));
        } catch (Exception e) {
            userBlockService.blockUser(String.valueOf(userId), message, "{}");
        }
    }

    private static double clampScore(double x) {
        if (x < 0) return 0;
        if (x > 100) return 100;
        return x;
    }

    private static String clientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            int comma = xf.indexOf(',');
            return comma > 0 ? xf.substring(0, comma).trim() : xf.trim();
        }
        return request.getRemoteAddr();
    }
}
