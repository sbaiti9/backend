package tn.esprit.user.security;

import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@EnableMethodSecurity
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final CustomUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          CustomUserDetailsService userDetailsService,
                          ObjectMapper objectMapper) {
        this.jwtFilter = jwtFilter;
        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
    }

    // --------------------------
    // PasswordEncoder
    // --------------------------
    /**
     * Préfixe ROLE_ pour {@link org.springframework.security.core.userdetails.User#builder()#roles(String...)}
     * et pour {@code hasRole("ADMIN")} côté sécurité HTTP.
     */
    @Bean
    static GrantedAuthorityDefaults grantedAuthorityDefaults() {
        return new GrantedAuthorityDefaults("ROLE_");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // --------------------------
    // AuthenticationManager
    // --------------------------
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    // --------------------------
    // AuthenticationProvider
    // --------------------------
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    // --------------------------
    // SecurityFilterChain
    // --------------------------
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .authenticationProvider(authenticationProvider())
                .authorizeHttpRequests(auth -> auth
                        // CORS preflight / clients directs — évite un refus avant que le POST n’atteigne le JwtFilter
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // AntPath (pas MvcRequestMatcher) : les POST avec variables de chemin matchent de façon fiable
                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher("/actuator/**"),
                                AntPathRequestMatcher.antMatcher("/user/auth/**"),
                                AntPathRequestMatcher.antMatcher("/user/internal/**"),
                                AntPathRequestMatcher.antMatcher("/internal/**"),
                                AntPathRequestMatcher.antMatcher("/user/admin/geo-security/users/**/hard-freeze"),
                                AntPathRequestMatcher.antMatcher("/h2-console/**"),
                                AntPathRequestMatcher.antMatcher("/v3/api-docs/**"),
                                AntPathRequestMatcher.antMatcher("/swagger-ui/**")
                        ).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/user/admin/**"))
                                // Accept both naming variants to avoid authority mapping mismatches.
                                .hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            // Pas d'authentification JWT dans le contexte (header absent, invalide, tv mismatch, compte gelé...)
                            Object rejectReason = request.getAttribute(JwtAuthenticationFilter.ATTR_JWT_REJECT_REASON);
                            String hint = rejectReason != null
                                    ? String.valueOf(rejectReason)
                                    : "missing-or-invalid-jwt";
                            response.setStatus(403);
                            response.setHeader("X-Skillio-Auth-Hint", hint);
                            response.setContentType("application/json;charset=UTF-8");
                            Map<String, String> body = new LinkedHashMap<>();
                            body.put("error", "Forbidden");
                            body.put("hint", hint);
                            response.getWriter().write(objectMapper.writeValueAsString(body));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                    Authentication a = SecurityContextHolder.getContext().getAuthentication();
                    String hint;
                    if (a == null) {
                        hint = "no-authentication";
                    } else if (a instanceof AnonymousAuthenticationToken) {
                        hint = "anonymous-or-jwt-not-applied-relogin-if-tv-mismatch";
                    } else {
                        String roles = a.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .collect(Collectors.joining(","));
                        hint = "user=" + a.getName() + ";authorities=" + roles;
                    }
                    response.setStatus(403);
                    response.setHeader("X-Skillio-Auth-Hint", hint);
                    response.setContentType("application/json;charset=UTF-8");
                    Map<String, String> body = new LinkedHashMap<>();
                    body.put("error", "Forbidden");
                    body.put("hint", hint);
                    response.getWriter().write(objectMapper.writeValueAsString(body));
                }));

        // Désactive frameOptions pour H2 console
        http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()));

        // JWT avant UsernamePassword : pratique courante ; AnonymousAuthenticationFilter ne remplace pas si déjà défini
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // --------------------------
    // CORS Global
    // --------------------------

}