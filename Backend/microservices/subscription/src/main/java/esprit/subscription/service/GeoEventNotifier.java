package esprit.subscription.service;

import esprit.subscription.DTO.GeoEventIngestBody;
import esprit.subscription.client.UserInternalGeoClient;
import esprit.subscription.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class GeoEventNotifier {

    private static final Logger log = LoggerFactory.getLogger(GeoEventNotifier.class);

    private final UserInternalGeoClient userInternalGeoClient;

    public GeoEventNotifier(UserInternalGeoClient userInternalGeoClient) {
        this.userInternalGeoClient = userInternalGeoClient;
    }

    public void notifyPaymentAsync(Long userId, HttpServletRequest request) {
        if (userId == null || request == null) {
            return;
        }
        String ip = ClientIpResolver.resolve(request);
        if (ip.isBlank()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                GeoEventIngestBody body = new GeoEventIngestBody();
                body.setUserId(userId);
                body.setEventType("PAYMENT");
                body.setClientIp(ip);
                userInternalGeoClient.postGeoEvent(body);
            } catch (Exception e) {
                log.debug("Geo payment ingest skipped: {}", e.getMessage());
            }
        });
    }
}
