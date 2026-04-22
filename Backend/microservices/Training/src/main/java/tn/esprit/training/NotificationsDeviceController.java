package tn.esprit.training;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.training.service.PushNotificationService;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationsDeviceController {
    private final PushNotificationService push;

    public NotificationsDeviceController(PushNotificationService push) {
        this.push = push;
    }

    @PostMapping("/register-token")
    public ResponseEntity<Void> register(@RequestBody Map<String, String> payload) {
        String token = payload.get("token");
        if (token != null && !token.isBlank()) {
            push.subscribeTokenToGlobal(token);
        }
        return ResponseEntity.ok().build();
    }
}

