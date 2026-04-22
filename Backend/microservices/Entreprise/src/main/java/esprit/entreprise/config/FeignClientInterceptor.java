package esprit.entreprise.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class FeignClientInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // ✅ Récupérer le token JWT de la requête actuelle
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String token = request.getHeader("Authorization");

            if (token != null && !token.isEmpty()) {
                System.out.println("📡 Feign Interceptor: Ajout token Authorization");
                // ✅ Ajouter le token à la requête Feign
                template.header("Authorization", token);
            } else {
                System.out.println("⚠️ Feign Interceptor: Pas de token trouvé");
            }
        } else {
            System.out.println("⚠️ Feign Interceptor: Pas de ServletRequestAttributes");
        }
    }
}