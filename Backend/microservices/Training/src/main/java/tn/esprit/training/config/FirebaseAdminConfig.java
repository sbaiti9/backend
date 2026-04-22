package tn.esprit.training.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;

@Configuration
public class FirebaseAdminConfig {

    @Value("${firebase.credentials.path:}")
    private String credentialsPath;

    @PostConstruct
    public void init() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                String path = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
                if (path == null || path.isBlank()) {
                    path = credentialsPath;
                }
                if (path == null || path.isBlank()) {
                    return;
                }
                FileInputStream serviceAccount = new FileInputStream(path);
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();
                FirebaseApp.initializeApp(options);
            }
        } catch (Exception ignored) {
        }
    }
}

