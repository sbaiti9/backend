package esprit.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user", r -> r.path("/user/**")
                        .uri("http://10.106.35.133:8080"))
                .route("entreprise", r -> r.path("/entreprise/**")
                        .uri("http://10.107.91.85:8080"))
                .route("subscription", r -> r.path("/sub/**")
                        .uri("http://10.101.248.217:8089"))
                .route("subscription-api-ai", r -> r.path("/api/ai/**")
                        .uri("http://10.101.248.217:8089"))
                .route("subscription-liveness", r -> r.path("/api/liveness/**")
                        .uri("http://10.101.248.217:8089"))
                .route("subscription-unlock-chat", r -> r.path("/api/unlock-chat/**")
                        .uri("http://10.101.248.217:8089"))
                .route("subscription-admin-notifications", r -> r.path("/api/admin/notifications/**")
                        .uri("http://10.101.248.217:8089"))
                .route("subscription-admin-blocks", r -> r.path("/api/admin/blocks/**")
                        .uri("http://10.101.248.217:8089"))
                .route("event", r -> r.path("/event/**")
                        .uri("http://10.107.23.197:8080"))
                .route("training", r -> r.path("/training/**")
                        .uri("http://10.104.87.89:8080"))
                .route("training-uploads", r -> r.path("/uploads/**")
                        .uri("http://10.104.87.89:8080"))
                .route("training-reviews", r -> r.path("/reviews/**")
                        .uri("http://10.104.87.89:8080"))
                .route("training-trainings", r -> r.path("/trainings/**")
                        .uri("http://10.104.87.89:8080"))
                .build();
    }
}
