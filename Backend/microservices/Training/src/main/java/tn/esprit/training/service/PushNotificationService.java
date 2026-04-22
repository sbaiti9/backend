package tn.esprit.training.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.springframework.stereotype.Service;

@Service
public class PushNotificationService {

    public void notifyTrainingCreated(String title) {
        sendToGlobal("New training available: " + title);
    }

    public void notifyReviewAdded(String trainingTitle) {
        sendToGlobal("New review added for " + trainingTitle);
    }

    public void subscribeTokenToGlobal(String token) {
        try {
            FirebaseMessaging.getInstance().subscribeToTopic(java.util.List.of(token), "global");
        } catch (Exception ignored) {
        }
    }

    private void sendToGlobal(String body) {
        try {
            Message message = Message.builder()
                    .putData("title", "Skillio")
                    .putData("body", body)
                    .setTopic("global")
                    .build();
            FirebaseMessaging.getInstance().send(message);
        } catch (Exception ignored) {
        }
    }
}

