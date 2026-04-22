package esprit.entreprise.config;

import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignClientConfig {

    @Autowired(required = false)
    private FeignClientInterceptor feignClientInterceptor;

    // ✅ AJOUTER CETTE MÉTHODE
    @Bean
    public RequestInterceptor requestInterceptor() {
        return feignClientInterceptor;
    }

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new CustomErrorDecoder();
    }

    public static class CustomErrorDecoder implements ErrorDecoder {
        @Override
        public Exception decode(String methodKey, feign.Response response) {
            String message = String.format(
                    "❌ Erreur Feign [%s] - Status: %d - Reason: %s",
                    methodKey, response.status(), response.reason()
            );
            return new RuntimeException(message);
        }
    }
}