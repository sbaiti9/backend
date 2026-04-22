package tn.esprit.training.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StaticContentConfig implements WebMvcConfigurer {

    @Value("${app.upload.contents-dir:uploads/contents}")
    private String contentsDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path contentsPath = Paths.get(contentsDir).toAbsolutePath().normalize();
        Path uploadsRoot = contentsPath.getParent();
        if (uploadsRoot == null) {
            uploadsRoot = contentsPath;
        }
        String location = uploadsRoot.toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}

