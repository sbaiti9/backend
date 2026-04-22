package tn.esprit.user.dto;

import java.util.List;
import java.util.Map;

/**
 * Admin report (AI-ready) for user activity (logins per day).
 */
public record UserActivityAiReport(
        int days,
        long totalLogins,
        long uniqueUsers,
        long impossibleTravelEvents,
        Map<String, Long> loginsByDayIso,
        List<TopUser> topUsers,
        String aiSummary
) {
    public record TopUser(Long userId, String name, String email, Long logins) {}
}

