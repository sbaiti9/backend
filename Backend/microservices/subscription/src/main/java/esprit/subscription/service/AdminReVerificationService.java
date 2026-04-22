package esprit.subscription.service;

import esprit.subscription.entity.BlockStatus;
import esprit.subscription.entity.UserBlock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminReVerificationService {

    private static final int TTL_MINUTES = 15;

    public record ReVerificationRequest(
            String userId,
            String token,
            Long blockId,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,
            boolean used
    ) {}

    private final Map<String, ReVerificationRequest> byToken = new ConcurrentHashMap<>();
    private final UserBlockService userBlockService;

    public AdminReVerificationService(UserBlockService userBlockService) {
        this.userBlockService = userBlockService;
    }

    /**
     * Admin triggers re-check: ensure block is in review pipeline and return a one-time token.
     */
    @Transactional
    public ReVerificationTokenResult requireReVerification(Long blockId, String adminUsername) {
        UserBlock ub = userBlockService.getByIdOrThrow(blockId);
        if (ub.getStatus() != BlockStatus.PENDING_REVIEW && ub.getStatus() != BlockStatus.BLOCKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Statut de blocage incompatible");
        }
        if (ub.getStatus() == BlockStatus.BLOCKED) {
            ub.setStatus(BlockStatus.PENDING_REVIEW);
            userBlockService.save(ub);
        }
        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime exp = now.plusMinutes(TTL_MINUTES);
        ReVerificationRequest req = new ReVerificationRequest(
                ub.getUserId(),
                token,
                blockId,
                now,
                exp,
                false
        );
        byToken.put(token, req);
        return new ReVerificationTokenResult(token, exp);
    }

    public record ReVerificationTokenResult(String token, LocalDateTime expiresAt) {}

    /**
     * Validates token for starting the client flow (does not consume — user may refresh precheck).
     */
    public boolean validateReVerificationToken(String token, String userId) {
        if (token == null || token.isBlank() || userId == null || userId.isBlank()) return false;
        ReVerificationRequest r = byToken.get(token.trim());
        if (r == null) return false;
        if (r.used()) return false;
        if (!r.userId().equals(userId.trim())) return false;
        return !LocalDateTime.now().isAfter(r.expiresAt());
    }

    /**
     * Marks token used after successful liveness re-verification.
     */
    @Transactional
    public boolean completeReVerification(String token, String userId) {
        if (token == null || token.isBlank() || userId == null || userId.isBlank()) return false;
        ReVerificationRequest r = byToken.get(token.trim());
        if (r == null) return false;
        if (r.used()) return false;
        if (!r.userId().equals(userId.trim())) return false;
        if (LocalDateTime.now().isAfter(r.expiresAt())) return false;

        byToken.put(token.trim(), new ReVerificationRequest(
                r.userId(), r.token(), r.blockId(), r.createdAt(), r.expiresAt(), true));

        UserBlock ub = userBlockService.getByIdOrThrow(r.blockId());
        ub.setReVerificationPassedAt(LocalDateTime.now());
        userBlockService.save(ub);
        return true;
    }

    /** Spec-compatible: validate and consume in one call (optional). */
    public boolean validateAndConsumeToken(String token, String userId) {
        return completeReVerification(token, userId);
    }
}
