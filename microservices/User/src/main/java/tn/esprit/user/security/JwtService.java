package tn.esprit.user.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.esprit.user.entity.Users;

import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    public static final String CLAIM_TOKEN_VERSION = "tv";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private Key getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(Users user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);
        int tv = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim(CLAIM_TOKEN_VERSION, tv)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    public int extractTokenVersion(String token) {
        Integer v = parseClaims(token).get(CLAIM_TOKEN_VERSION, Integer.class);
        return v != null ? v : 0;
    }

    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
