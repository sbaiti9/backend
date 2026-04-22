package esprit.subscription.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class LivenessJwtService {

    private final SecretKey key;

    public LivenessJwtService(@Value("${liveness.jwt.secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("liveness.jwt.secret must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(long userId, String challengeToken) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(5 * 60L);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .claim("challenge", challengeToken)
                .claim("verified", true)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    /**
     * @return null if invalid
     */
    public Claims parseAndValidate(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isValidForPayment(String token, long expectedUserId) {
        Claims claims = parseAndValidate(token);
        if (claims == null) {
            return false;
        }
        Boolean verified = claims.get("verified", Boolean.class);
        if (verified == null || !verified) {
            return false;
        }
        String sub = claims.getSubject();
        if (sub == null) {
            return false;
        }
        try {
            return Long.parseLong(sub) == expectedUserId;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
