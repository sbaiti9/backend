package tn.esprit.user.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tn.esprit.user.entity.Status;
import tn.esprit.user.entity.Users;
import tn.esprit.user.repository.UserRepository;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ATTR_JWT_REJECT_REASON = "skillio.jwt.reject.reason";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomUserDetailsService uds,
                                   UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userDetailsService = uds;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String rawAuth = request.getHeader("Authorization");
        if (rawAuth == null || rawAuth.isBlank()) {
            // Fallback for proxies/clients where Authorization can be stripped.
            rawAuth = request.getHeader("X-Auth-Token");
        }
        if (rawAuth == null || rawAuth.isBlank()) {
            // Secondary fallback used by some clients and tooling.
            rawAuth = request.getParameter("access_token");
        }

        String token = extractBearerOrRawToken(rawAuth);
        if (token == null || token.isBlank()) {
            request.setAttribute(ATTR_JWT_REJECT_REASON, "missing-auth-header");
            filterChain.doFilter(request, response);
            return;
        }
        token = token.trim();

        try {
            Claims claims = jwtService.parseClaims(token);
            String username = claims.getSubject() != null ? claims.getSubject().trim() : null;

            // Toujours appliquer le JWT quand un Bearer est présent (ne pas exiger auth == null) :
            // sinon un contexte résiduel / anonyme peut empêcher de charger ROLE_ADMIN sur certains POST.
            if (username != null && !username.isEmpty()) {
                Optional<Users> userOpt = userRepository.findByEmail(username);
                if (userOpt.isEmpty()) {
                    request.setAttribute(ATTR_JWT_REJECT_REASON, "subject-not-found");
                    log.warn("JWT subject not found in DB (email={})", username);
                    filterChain.doFilter(request, response);
                    return;
                }
                if (userOpt.get().getStatus() == Status.FROZEN) {
                    request.setAttribute(ATTR_JWT_REJECT_REASON, "account-frozen");
                    log.warn("JWT rejected: account FROZEN (email={})", username);
                    filterChain.doFilter(request, response);
                    return;
                }
                Users user = userOpt.get();
                int claimTv = jwtService.extractTokenVersion(token);
                int dbTv = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
                if (!Objects.equals(claimTv, dbTv)) {
                    request.setAttribute(ATTR_JWT_REJECT_REASON, "token-version-mismatch");
                    log.warn(
                            "JWT rejected: tokenVersion mismatch claim={} db={} (email={}) — reconnect to obtain a new token",
                            claimTv, dbTv, username);
                    filterChain.doFilter(request, response);
                    return;
                }

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

        } catch (Exception e) {
            request.setAttribute(ATTR_JWT_REJECT_REASON, "token-parse-failed");
            log.warn("JWT parse/validation failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /** Accepte "Bearer " / "bearer " (proxies / HTTP2 peuvent normaliser la casse). */
    private static boolean regionMatchesIgnoreCase(String value, String prefix, int start) {
        if (value.length() - start < prefix.length()) {
            return false;
        }
        return value.regionMatches(true, start, prefix, 0, prefix.length());
    }

    /**
     * Accept both "Bearer <jwt>" and plain "<jwt>" to be resilient
     * against intermediaries that rewrite auth headers.
     */
    private static String extractBearerOrRawToken(String rawAuth) {
        if (rawAuth == null) {
            return null;
        }
        String trimmed = rawAuth.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (regionMatchesIgnoreCase(trimmed, "Bearer ", 0)) {
            return trimmed.substring(7).trim();
        }
        // Support direct token value as fallback.
        return trimmed;
    }
}
