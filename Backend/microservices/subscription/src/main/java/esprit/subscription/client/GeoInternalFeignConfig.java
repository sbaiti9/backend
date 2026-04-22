package esprit.subscription.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class GeoInternalFeignConfig {

    @Bean
    public RequestInterceptor geoInternalApiKeyInterceptor(@Value("${skillio.internal.api-key:}") String apiKey) {
        return template -> {
            if (apiKey != null && !apiKey.isBlank()) {
                template.header("X-Internal-Key", apiKey);
            }
        };
    }
}
