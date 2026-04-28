package tn.esprit.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import tn.esprit.user.dto.UserActivityAiReport;
import tn.esprit.user.entity.GeoEventType;
import tn.esprit.user.entity.Users;
import tn.esprit.user.repository.GeoAuthEventRepository;
import tn.esprit.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserActivityAiService {

    private final GeoAuthEventRepository geoAuthEventRepository;
    private final UserRepository userRepository;
    private final GroqChatClient groqChatClient;
    private final ObjectMapper objectMapper;

    public UserActivityAiService(GeoAuthEventRepository geoAuthEventRepository,
                                 UserRepository userRepository,
                                 GroqChatClient groqChatClient,
                                 ObjectMapper objectMapper) {
        this.geoAuthEventRepository = geoAuthEventRepository;
        this.userRepository = userRepository;
        this.groqChatClient = groqChatClient;
        this.objectMapper = objectMapper;
    }

    public UserActivityAiReport buildLoginActivityReport(int days) {
        int safeDays = Math.max(1, Math.min(180, days));
        LocalDateTime since = LocalDateTime.now().minusDays(safeDays);

        Map<String, Long> byDay = new LinkedHashMap<>();
        // Fill missing days with 0 (for charts)
        LocalDate start = LocalDate.now().minusDays(safeDays - 1L);
        for (int i = 0; i < safeDays; i++) {
            byDay.put(start.plusDays(i).toString(), 0L);
        }

        long total = 0L;
        for (Object[] row : geoAuthEventRepository.countByDaySince(GeoEventType.LOGIN, since)) {
            if (row == null || row.length < 2) continue;
            Object dObj = row[0];
            Object cObj = row[1];
            String day = dObj != null ? String.valueOf(dObj) : null;
            long c = cObj instanceof Number ? ((Number) cObj).longValue() : 0L;
            if (day != null) {
                byDay.put(day, c);
            }
            total += c;
        }

        List<Object[]> topRows = geoAuthEventRepository.topUsersByEventsSince(GeoEventType.LOGIN, since);
        List<UserActivityAiReport.TopUser> topUsers = new ArrayList<>();
        long uniqueUsers = 0L;
        if (topRows != null) {
            uniqueUsers = topRows.size();

            List<Long> topIds = topRows.stream()
                    .limit(10)
                    .map(r -> (r != null && r.length > 0 && r[0] instanceof Number) ? ((Number) r[0]).longValue() : null)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());

            Map<Long, Users> byId = new HashMap<>();
            if (!topIds.isEmpty()) {
                for (Users u : userRepository.findAllById(topIds)) {
                    if (u != null && u.getId() != null) {
                        byId.put(u.getId(), u);
                    }
                }
            }

            for (int i = 0; i < Math.min(10, topRows.size()); i++) {
                Object[] r = topRows.get(i);
                if (r == null || r.length < 2) continue;
                Long userId = (r[0] instanceof Number) ? ((Number) r[0]).longValue() : null;
                Long c = (r[1] instanceof Number) ? ((Number) r[1]).longValue() : 0L;
                if (userId != null) {
                    Users u = byId.get(userId);
                    String name = u != null ? u.getName() : null;
                    String email = u != null ? u.getEmail() : null;
                    topUsers.add(new UserActivityAiReport.TopUser(userId, name, email, c));
                }
            }
        }

        long impossible = geoAuthEventRepository.countImpossibleTravelSince(since);

        String summary = generateAiSummary(safeDays, total, uniqueUsers, impossible, byDay, topUsers);
        return new UserActivityAiReport(safeDays, total, uniqueUsers, impossible, byDay, topUsers, summary);
    }

    private String generateAiSummary(int days,
                                     long total,
                                     long uniqueUsers,
                                     long impossible,
                                     Map<String, Long> byDay,
                                     List<UserActivityAiReport.TopUser> topUsers) {
        // First try Groq (optional)
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("days", days);
            payload.put("totalLogins", total);
            payload.put("uniqueUsers", uniqueUsers);
            payload.put("impossibleTravelEvents", impossible);
            payload.put("loginsByDayIso", byDay);
            payload.put("topUsers", topUsers);

            String system = """
                    You are a security & product analyst for a training platform.
                    Given login activity metrics, produce a concise JSON with keys:
                    summary (string), insights (array of strings), risks (array of strings), recommendations (array of strings).
                    Keep it short and actionable. Do not invent data.
                    """;
            String user = objectMapper.writeValueAsString(payload);
            Optional<String> groq = groqChatClient.completeJson(system, user, 0.25);
            if (groq.isPresent() && !groq.get().isBlank()) {
                return groq.get();
            }
        } catch (Exception ignored) {
        }

        // Fallback heuristic "AI-style" summary
        long peak = 0L;
        String peakDay = null;
        for (var e : byDay.entrySet()) {
            if (e.getValue() != null && e.getValue() > peak) {
                peak = e.getValue();
                peakDay = e.getKey();
            }
        }
        String top = topUsers.isEmpty()
                ? "Top user: N/A"
                : ("Top user: userId=" + topUsers.get(0).userId() + " (" + topUsers.get(0).logins() + " logins)");

        return String.join("\n",
                "Summary:",
                "- Period: last " + days + " day(s)",
                "- Total logins: " + total,
                "- Unique users (logged in): " + uniqueUsers,
                "- Impossible-travel events: " + impossible,
                "- Peak day: " + (peakDay != null ? peakDay + " (" + peak + ")" : "N/A"),
                "- " + top,
                "",
                "Recommendations:",
                "- Investigate accounts with repeated geo anomalies (impossible travel).",
                "- Monitor brute-force locks (423) and enable MFA for high-risk users."
        );
    }
}

