package esprit.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    /**
     * Si vide : load-balancing Eureka {@code lb://entreprise}. Sinon : URL directe (ex. {@code http://127.0.0.1:8083})
     * quand Eureka n’est pas démarré — évite le 503 « Service Unavailable » côté gateway.
     * <p>Même logique pour {@code subscription} via {@code gateway.services.subscription-direct-uri}, pour
     * {@code user} via {@code gateway.services.user-direct-uri}, et pour {@code event} / {@code Training} via
     * {@code gateway.services.event-direct-uri} et {@code gateway.services.training-direct-uri} (Kubernetes sans Eureka).</p>
     * <p>Route {@code /sub/unlock-chat/**} → subscription <strong>sans</strong> réécriture : le contrôleur expose déjà
     * {@code @RequestMapping("/sub/unlock-chat")} en plus de {@code /api/unlock-chat}. Éviter {@code RewritePath}
     * ici : selon les versions, la réécriture peut casser les POST (404 sur grant/approve alors que les GET passent).</p>
     */
    @Bean
    public RouteLocator gatewayRoutes(
            RouteLocatorBuilder builder,
            @Value("${gateway.services.entreprise-direct-uri:}") String entrepriseDirectUri,
            @Value("${gateway.services.subscription-direct-uri:}") String subscriptionDirectUri,
            @Value("${gateway.services.user-direct-uri:}") String userDirectUri,
            @Value("${gateway.services.event-direct-uri:}") String eventDirectUri,
            @Value("${gateway.services.training-direct-uri:}") String trainingDirectUri) {

        String entrepriseTarget = resolveHttpOrLbUri(entrepriseDirectUri, "lb://entreprise");
        String subscriptionTarget = resolveHttpOrLbUri(subscriptionDirectUri, "lb://subscription");
        String userTarget = resolveHttpOrLbUri(userDirectUri, "lb://user");
        String eventTarget = resolveHttpOrLbUri(eventDirectUri, "lb://event");
        String trainingTarget = resolveHttpOrLbUri(trainingDirectUri, "lb://Training");

        // URIs lb://* must match Eureka `spring.application.name` exactly (case-sensitive).
        // Plus spécifique en premier : /sub/unlock-chat/** réécrit avant la route générale /sub/**.
        return builder.routes()
                .route("subscription-unlock-chat-sub-prefix", r -> r.path("/sub/unlock-chat/**")
                        .uri(subscriptionTarget))

                // Search routes (more specific than the general /{service}/** routes)
                .route("user-search", r -> r.path("/user/search/**")
                        .uri(userTarget))
                .route("event-search", r -> r.path("/event/search/**")
                        .uri(eventTarget))
                .route("training-search", r -> r.path("/api/trainings/search/**")
                        .uri(trainingTarget))
                .route("entreprise-search", r -> r.path("/entreprise/job-offers/search/**")
                        .uri(entrepriseTarget))
                .route("subscription-search", r -> r.path("/sub/plans/search/**")
                        .uri(subscriptionTarget))

                .route("user", r -> r.path("/user/**")
                        .uri(userTarget))
                .route("Entreprise", r -> r.path("/entreprise/**")
                        .uri(entrepriseTarget))
                .route("subscription", r -> r.path("/sub/**")
                        .uri(subscriptionTarget))
                .route("subscription-api-ai", r -> r.path("/api/ai/**")
                        .uri(subscriptionTarget))
                .route("subscription-liveness", r -> r.path("/api/liveness/**")
                        .uri(subscriptionTarget))
                .route("subscription-unlock-chat", r -> r.path("/api/unlock-chat/**")
                        .uri(subscriptionTarget))
                .route("subscription-admin-notifications", r -> r.path("/api/admin/notifications/**")
                        .uri(subscriptionTarget))
                .route("subscription-admin-blocks", r -> r.path("/api/admin/blocks/**")
                        .uri(subscriptionTarget))

                .route("event", r -> r.path("/event/**")
                        .uri(eventTarget))

                .route("training-uploads", r -> r.path("/uploads/**")
                        .uri(trainingTarget))
                .route("training-reviews", r -> r.path("/reviews/**")
                        .uri(trainingTarget))
                .route("training-trainings", r -> r.path("/trainings/**")
                        .uri(trainingTarget))

                .build();
    }

    private static String resolveHttpOrLbUri(String directUri, String lbFallback) {
        if (!StringUtils.hasText(directUri)) {
            return lbFallback;
        }
        String u = directUri.trim();
        if (u.startsWith("http://") || u.startsWith("https://")) {
            return u;
        }
        return "http://" + u;
    }
}
