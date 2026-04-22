package esprit.subscription.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseStreamService {

    /** 0 = no servlet timeout; connections still end when client/gateway disconnects. */
    private static final long SSE_NO_TIMEOUT_MS = 0L;

    private final ObjectMapper objectMapper;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    /** Separate from unlock-chat admin stream ({@link #subscribeAdmin()}). */
    private final Map<String, SseEmitter> adminNotificationEmitters = new ConcurrentHashMap<>();

    public SseStreamService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter subscribeAdmin() {
        SseEmitter emitter = new SseEmitter(SSE_NO_TIMEOUT_MS);
        emitters.put("admin", emitter);
        Runnable onEnd = () -> emitters.remove("admin", emitter);
        emitter.onCompletion(onEnd);
        emitter.onTimeout(onEnd);
        emitter.onError(e -> onEnd.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"channel\":\"admin\"}"));
        } catch (Exception e) {
            emitters.remove("admin", emitter);
        }
        return emitter;
    }

    public SseEmitter subscribeUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId required");
        }
        String key = userId.trim();
        SseEmitter emitter = new SseEmitter(SSE_NO_TIMEOUT_MS);
        emitters.put(key, emitter);
        Runnable onEnd = () -> emitters.remove(key, emitter);
        emitter.onCompletion(onEnd);
        emitter.onTimeout(onEnd);
        emitter.onError(e -> onEnd.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"channel\":\"user\"}"));
        } catch (Exception e) {
            emitters.remove(key, emitter);
        }
        return emitter;
    }

    /**
     * SSE channel dedicated to admin notification alerts (not the unlock-chat inbox stream).
     */
    public SseEmitter subscribeAdminNotifications() {
        SseEmitter emitter = new SseEmitter(SSE_NO_TIMEOUT_MS);
        String id = UUID.randomUUID().toString();
        adminNotificationEmitters.put(id, emitter);
        Runnable onEnd = () -> adminNotificationEmitters.remove(id, emitter);
        emitter.onCompletion(onEnd);
        emitter.onTimeout(onEnd);
        emitter.onError(e -> onEnd.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"channel\":\"admin-notifications\"}"));
        } catch (Exception e) {
            adminNotificationEmitters.remove(id, emitter);
        }
        return emitter;
    }

    /** Broadcasts to every open admin notification SSE connection. */
    public void sendToAdmins(String eventName, Object payload) {
        if (adminNotificationEmitters.isEmpty()) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return;
        }
        for (Map.Entry<String, SseEmitter> e : adminNotificationEmitters.entrySet()) {
            try {
                e.getValue().send(SseEmitter.event().name(eventName).data(json));
            } catch (Exception ex) {
                adminNotificationEmitters.remove(e.getKey(), e.getValue());
            }
        }
    }

    public void pushToAdmin(String eventName, Object payload) {
        SseEmitter emitter = emitters.get("admin");
        if (emitter == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (Exception e) {
            emitters.remove("admin", emitter);
        }
    }

    public void pushToUser(String userId, String eventName, Object payload) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        String key = userId.trim();
        SseEmitter emitter = emitters.get(key);
        if (emitter == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (Exception e) {
            emitters.remove(key, emitter);
        }
    }
}
