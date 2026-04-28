package esprit.subscription.service;

import esprit.subscription.entity.BlockStatus;
import esprit.subscription.entity.UserBlock;
import esprit.subscription.event.UserBlockedEvent;
import esprit.subscription.repository.UserBlockRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserBlockService {

    private final UserBlockRepository userBlockRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UserBlockService(UserBlockRepository userBlockRepository, ApplicationEventPublisher eventPublisher) {
        this.userBlockRepository = userBlockRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public UserBlock blockUser(String userId, String reason, String sessionPayload) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId required");
        }
        Optional<UserBlock> existing = userBlockRepository.findByUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        if (existing.isPresent()) {
            UserBlock ub = existing.get();
            ub.setFailureCount(ub.getFailureCount() + 1);
            ub.setBlockReason(reason != null ? reason : ub.getBlockReason());
            ub.setLastSessionPayload(sessionPayload);
            ub.setStatus(BlockStatus.BLOCKED);
            ub.setBlockedAt(now);
            UserBlock saved = userBlockRepository.save(ub);
            eventPublisher.publishEvent(new UserBlockedEvent(saved.getUserId(), reason, saved.getId()));
            return saved;
        }
        UserBlock ub = UserBlock.builder()
                .userId(userId)
                .blockReason(reason != null ? reason : "Échec vérification liveness")
                .blockedAt(now)
                .status(BlockStatus.BLOCKED)
                .failureCount(1)
                .lastSessionPayload(sessionPayload)
                .build();
        UserBlock saved = userBlockRepository.save(ub);
        eventPublisher.publishEvent(new UserBlockedEvent(saved.getUserId(), reason, saved.getId()));
        return saved;
    }

    /**
     * True when the account is fully locked (no liveness challenge).
     * {@link BlockStatus#PENDING_REVIEW} users may still complete a re-verification session.
     */
    @Transactional(readOnly = true)
    public boolean isLivenessForbidden(String userId) {
        if (userId == null || userId.isBlank()) return false;
        return userBlockRepository.findByUserId(userId)
                .map(ub -> ub.getStatus() == BlockStatus.BLOCKED)
                .orElse(false);
    }

    /** Payment and other sensitive actions while a block or review is open. */
    @Transactional(readOnly = true)
    public boolean isPaymentBlocked(String userId) {
        if (userId == null || userId.isBlank()) return false;
        return userBlockRepository.findByUserId(userId)
                .map(ub -> ub.getStatus() == BlockStatus.BLOCKED || ub.getStatus() == BlockStatus.PENDING_REVIEW)
                .orElse(false);
    }

    @Transactional
    public UserBlock requestUnlock(String userId) {
        UserBlock ub = userBlockRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun blocage enregistré"));
        if (ub.getStatus() != BlockStatus.BLOCKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le compte n'est pas en attente de déblocage utilisateur");
        }
        ub.setStatus(BlockStatus.PENDING_REVIEW);
        return userBlockRepository.save(ub);
    }

    @Transactional
    public UserBlock approveUnlock(Long blockId, String adminUsername) {
        UserBlock ub = userBlockRepository.findById(blockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blocage introuvable"));
        ub.setStatus(BlockStatus.UNLOCKED);
        ub.setUnlockedAt(LocalDateTime.now());
        ub.setUnlockedBy(adminUsername != null ? adminUsername : "admin");
        return userBlockRepository.save(ub);
    }

    @Transactional
    public UserBlock rejectUnlock(Long blockId, String adminUsername, String reason) {
        UserBlock ub = userBlockRepository.findById(blockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blocage introuvable"));
        ub.setStatus(BlockStatus.BLOCKED);
        String note = reason != null && !reason.isBlank() ? reason : "Demande rejetée";
        ub.setBlockReason(ub.getBlockReason() + " | Rejet admin (" + (adminUsername != null ? adminUsername : "?") + "): " + note);
        return userBlockRepository.save(ub);
    }

    @Transactional(readOnly = true)
    public Optional<UserBlock> findByUserId(String userId) {
        return userBlockRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Optional<UserBlock> findById(Long id) {
        return userBlockRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public UserBlock getByIdOrThrow(Long id) {
        return userBlockRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blocage introuvable"));
    }

    @Transactional
    public UserBlock save(UserBlock ub) {
        return userBlockRepository.save(ub);
    }

    public List<UserBlock> findPendingReview() {
        return userBlockRepository.findByStatusOrderByBlockedAtDesc(BlockStatus.PENDING_REVIEW);
    }

    public List<UserBlock> findAllBlocks() {
        return userBlockRepository.findAllByOrderByBlockedAtDesc();
    }

    public List<UserBlock> historyForUser(String userId) {
        return userBlockRepository.findByUserId(userId).map(List::of).orElseGet(List::of);
    }
}
