package tn.esprit.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.user.dto.ChurnBatchReport;
import tn.esprit.user.dto.ChurnUserPrediction;
import tn.esprit.user.entity.GeoAuthEvent;
import tn.esprit.user.entity.GeoEventType;
import tn.esprit.user.entity.Role;
import tn.esprit.user.entity.Users;
import tn.esprit.user.repository.GeoAuthEventRepository;
import tn.esprit.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Estime le risque qu’un utilisateur quitte ou déserte la plateforme Skillio.
 * Combine signaux factuels (logins GeoAuthEvent, points d’engagement) + Groq optionnel.
 */
@Service
@RequiredArgsConstructor
public class UserChurnPredictionService {

    private static final Logger log = LoggerFactory.getLogger(UserChurnPredictionService.class);

    private final UserRepository userRepository;
    private final GeoAuthEventRepository geoAuthEventRepository;
    private final GroqChatClient groqChatClient;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ChurnBatchReport buildReport(int days) {
        int window = Math.max(7, Math.min(90, days));
        LocalDateTime since = LocalDateTime.now().minusDays(window);

        List<Users> candidates = userRepository.findAll().stream()
                .filter(u -> u.getId() != null && u.getRole() != Role.ADMIN)
                .toList();

        List<ChurnUserPrediction> heuristics = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Users u : candidates) {
            GeoSnapshot snap = snapshot(u.getId(), since);
            heuristics.add(heuristic(u, snap));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", u.getId());
            row.put("email", u.getEmail());
            row.put("role", u.getRole() != null ? u.getRole().name() : "");
            row.put("platformPoints", Optional.ofNullable(u.getPlatformEngagementPoints()).orElse(0));
            row.put("loginsLastWindow", snap.loginCount());
            row.put("daysSinceLastLogin", snap.daysSinceLastLogin());
            rows.add(row);
        }

        String summaryFr = buildDefaultSummary(heuristics, window);
        List<ChurnUserPrediction> finalList = new ArrayList<>(heuristics);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(
                    Map.of("users", rows, "hint", "daysSinceLastLogin=9999 signifie aucun LOGIN enregistré"));
        } catch (Exception e) {
            payload = "{}";
        }

        Optional<String> groqJson = groqChatClient.completeJson(
                """
                        Tu es un analyste data pour la plateforme e-learning Skillio.
                        Réponds uniquement avec un JSON valide :
                        {"summaryFr":"...","predictions":[{"userId":123,"churnRisk":"LOW|MEDIUM|HIGH","leaveProbabilityPercent":0-100,"messageFr":"..."}]}
                        Règles :
                        - churnRisk : LOW actif, MEDIUM modéré, HIGH fort risque de désengagement.
                        - messageFr : une phrase courte par utilisateur (max 140 car.), français.
                        - summaryFr : 2 phrases max sur la cohorte.
                        - Priorise daysSinceLastLogin élevé, loginsLastWindow faible, platformPoints très bas.
                        - Inclus une entrée predictions pour chaque userId fourni.
                        """,
                payload,
                0.25
        );

        if (groqJson.isPresent()) {
            try {
                List<ChurnUserPrediction> parsed = mergeGroq(heuristics, groqJson.get());
                if (!parsed.isEmpty()) {
                    finalList = parsed;
                    JsonNode root = objectMapper.readTree(groqJson.get());
                    if (root.path("summaryFr").isTextual()) {
                        String s = root.path("summaryFr").asText("").trim();
                        if (!s.isEmpty()) {
                            summaryFr = s;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Churn Groq merge failed, heuristique seule: {}", e.getMessage());
            }
        }

        return new ChurnBatchReport(window, summaryFr, finalList);
    }

    private List<ChurnUserPrediction> mergeGroq(List<ChurnUserPrediction> heuristics, String rawJson) throws Exception {
        JsonNode root = objectMapper.readTree(rawJson);
        JsonNode preds = root.path("predictions");
        if (!preds.isArray()) {
            return List.of();
        }
        Map<Long, ChurnUserPrediction> baseById = new TreeMap<>();
        for (ChurnUserPrediction h : heuristics) {
            baseById.put(h.userId(), h);
        }
        Map<Long, ChurnUserPrediction> out = new TreeMap<>();
        for (JsonNode n : preds) {
            long uid = n.path("userId").asLong(0);
            if (uid <= 0 || !baseById.containsKey(uid)) {
                continue;
            }
            ChurnUserPrediction base = baseById.get(uid);
            String risk = normRisk(n.path("churnRisk").asText(base.churnRisk()));
            int pct = clamp(n.path("leaveProbabilityPercent").asInt(base.leaveProbabilityPercent()), 0, 100);
            String msg = n.path("messageFr").asText(base.messageFr()).trim();
            if (msg.length() > 200) {
                msg = msg.substring(0, 200) + "…";
            }
            out.put(uid, new ChurnUserPrediction(uid, base.email(), risk, pct, msg, "HYBRID"));
        }
        for (ChurnUserPrediction h : heuristics) {
            out.putIfAbsent(h.userId(), h);
        }
        return new ArrayList<>(out.values());
    }

    private static String normRisk(String r) {
        String u = r == null ? "" : r.trim().toUpperCase();
        if ("LOW".equals(u) || "MEDIUM".equals(u) || "HIGH".equals(u)) {
            return u;
        }
        return "MEDIUM";
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private GeoSnapshot snapshot(Long userId, LocalDateTime since) {
        Long cnt = geoAuthEventRepository.countLoginsSince(userId, GeoEventType.LOGIN, since);
        long logins = cnt != null ? cnt : 0L;
        Optional<GeoAuthEvent> last = geoAuthEventRepository.findTopByUserIdAndEventTypeOrderByCreatedAtDesc(
                userId, GeoEventType.LOGIN);
        long daysSince = last
                .map(e -> {
                    if (e.getCreatedAt() == null) {
                        return 9999L;
                    }
                    return ChronoUnit.DAYS.between(e.getCreatedAt(), LocalDateTime.now());
                })
                .orElse(9999L);
        return new GeoSnapshot(logins, daysSince);
    }

    private ChurnUserPrediction heuristic(Users u, GeoSnapshot snap) {
        int score = 0;
        if (snap.daysSinceLastLogin() >= 9999) {
            score += 45;
        } else if (snap.daysSinceLastLogin() > 45) {
            score += 40;
        } else if (snap.daysSinceLastLogin() > 21) {
            score += 28;
        } else if (snap.daysSinceLastLogin() > 7) {
            score += 12;
        }

        if (snap.loginCount() < 2) {
            score += 22;
        } else if (snap.loginCount() < 5) {
            score += 10;
        }

        int pts = Optional.ofNullable(u.getPlatformEngagementPoints()).orElse(0);
        if (pts < 30) {
            score += 18;
        } else if (pts < 100) {
            score += 8;
        }

        score = clamp(score, 0, 100);
        String risk = score < 35 ? "LOW" : (score < 65 ? "MEDIUM" : "HIGH");
        String msg;
        if ("HIGH".equals(risk)) {
            msg = "Peu d’activité récente ou engagement faible : risque élevé de désertion.";
        } else if ("MEDIUM".equals(risk)) {
            msg = "Signaux mitigés : encourager la réactivation (formations, notifications).";
        } else {
            msg = "Activité récente correcte : risque faible sur la fenêtre observée.";
        }
        return new ChurnUserPrediction(
                u.getId(),
                u.getEmail() != null ? u.getEmail() : "",
                risk,
                score,
                msg,
                "HEURISTIC"
        );
    }

    private String buildDefaultSummary(List<ChurnUserPrediction> preds, int window) {
        long high = preds.stream().filter(p -> "HIGH".equals(p.churnRisk())).count();
        long med = preds.stream().filter(p -> "MEDIUM".equals(p.churnRisk())).count();
        return String.format(
                "Sur une fenêtre de %d jours, %d utilisateur(s) hors ADMIN : %d à risque élevé, %d à risque modéré. "
                        + "Les signaux combinent les logins (GeoAuthEvent), les jours depuis le dernier login et les points plateforme.",
                window,
                preds.size(),
                high,
                med
        );
    }

    private record GeoSnapshot(long loginCount, long daysSinceLastLogin) {}
}
