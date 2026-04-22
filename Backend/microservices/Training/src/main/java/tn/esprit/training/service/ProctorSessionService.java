package tn.esprit.training.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.esprit.training.dto.ProctorSessionResponseDTO;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived quiz proctor sessions: binds a token to a course id and rate-limits analyze calls.
 */
@Service
public class ProctorSessionService {

    private static final class Session {
        final long courseId;
        final long expiresAtMs;
        volatile long lastAnalyzeMs;
        volatile int analyzeCount;
        volatile boolean faceCheckOk;

        Session(long courseId, long expiresAtMs) {
            this.courseId = courseId;
            this.expiresAtMs = expiresAtMs;
            this.lastAnalyzeMs = 0L;
            this.analyzeCount = 0;
            this.faceCheckOk = false;
        }
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @Value("${app.proctor.session-ttl-ms:7200000}")
    private long sessionTtlMs;

    @Value("${app.proctor.min-interval-ms:4000}")
    private long minIntervalMs;

    @Value("${app.proctor.max-analyzes-per-session:80}")
    private int maxAnalyzesPerSession;

    public ProctorSessionResponseDTO startSession(long courseId) {
        purgeExpired();
        long now = Instant.now().toEpochMilli();
        long exp = now + Math.max(60_000L, sessionTtlMs);
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new Session(courseId, exp));
        return new ProctorSessionResponseDTO(token, exp);
    }

    public void validate(String token, long courseId) {
        Session s = requireSession(token);
        if (s.courseId != courseId) {
            throw new IllegalArgumentException("Session does not match this course");
        }
    }

    public void markFaceCheckOk(String token) {
        Session s = sessions.get(token);
        if (s != null && Instant.now().toEpochMilli() <= s.expiresAtMs) {
            s.faceCheckOk = true;
        }
    }

    public boolean isFaceCheckOk(String token) {
        Session s = sessions.get(token);
        return s != null && s.faceCheckOk && Instant.now().toEpochMilli() <= s.expiresAtMs;
    }

    public void requireFaceCheckDoneForMonitor(String token) {
        if (!isFaceCheckOk(token)) {
            throw new IllegalArgumentException("Complete face check before monitoring");
        }
    }

    /**
     * @return true if this analyze call is allowed (rate / quota)
     */
    public boolean tryConsumeAnalyzeSlot(String token) {
        Session s = requireSession(token);
        long now = Instant.now().toEpochMilli();
        if (s.analyzeCount >= maxAnalyzesPerSession) {
            return false;
        }
        if (s.lastAnalyzeMs > 0 && now - s.lastAnalyzeMs < minIntervalMs) {
            return false;
        }
        s.lastAnalyzeMs = now;
        s.analyzeCount++;
        return true;
    }

    private Session requireSession(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("sessionToken is required");
        }
        Session s = sessions.get(token);
        long now = Instant.now().toEpochMilli();
        if (s == null || now > s.expiresAtMs) {
            sessions.remove(token);
            throw new IllegalArgumentException("Invalid or expired proctor session");
        }
        return s;
    }

    private void purgeExpired() {
        long now = Instant.now().toEpochMilli();
        sessions.entrySet().removeIf(e -> now > e.getValue().expiresAtMs);
    }
}
