package esprit.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS pour Angular → gateway (port 8091). Configuration centralisée ici (pas dans application.yml).
 * Ne pas activer en plus {@code spring.cloud.gateway.globalcors} — un seul mécanisme pour éviter les en-têtes dupliqués.
 * {@code RemoveResponseHeader} puis {@code DedupeResponseHeader} dans application.yml évitent les doublons si un microservice renvoie aussi Access-Control-*.
 */
@Configuration
@Profile("!k8s-nginx-cors")
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:4200",
                "http://127.0.0.1:4200",
                "http://192.168.33.10:30095"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        // Avec allowCredentials=true, "*" pour les en-têtes autorisés est peu fiable pour Authorization (preflight).
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "X-Liveness-Admin-Secret",
                "x-liveness-admin-secret",
                "X-Liveness-Token",
                "x-liveness-token",
                "X-Auth-Token",
                "x-auth-token",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"));
        config.setExposedHeaders(List.of(
                "X-Liveness-Token",
                "Authorization",
                "X-Skillio-Auth-Hint"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
