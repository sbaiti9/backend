package esprit.subscription.controller;

import esprit.subscription.entity.Subscription;
import esprit.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/sub")
@RequiredArgsConstructor
public class SubscriptionSearchController {

    private final SubscriptionRepository repo;

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userRole,
            @RequestParam(required = false) String billingCycle) {
        try {
            List<Subscription> all = repo.findAll();
            List<Map<String, Object>> results = all.stream()
                .filter(s -> status == null || status.isBlank() || s.getStatus().equalsIgnoreCase(status))
                .filter(s -> userRole == null || userRole.isBlank() || (s.getUserRole() != null && s.getUserRole().equalsIgnoreCase(userRole)))
                .filter(s -> billingCycle == null || billingCycle.isBlank() || s.getBillingCycle().equalsIgnoreCase(billingCycle))
                .map(this::toMap)
                .collect(Collectors.toList());
            return ResponseEntity.ok(Map.of("subscriptions", results, "total", results.size()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    @GetMapping("/search/user/{userId}")
    public ResponseEntity<?> searchByUser(@PathVariable Long userId) {
        try {
            List<Map<String, Object>> results = repo.findByUserId(userId)
                .stream().map(this::toMap).collect(Collectors.toList());
            return ResponseEntity.ok(Map.of("subscriptions", results, "total", results.size()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    private Map<String, Object> toMap(Subscription s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("userId", s.getUserId());
        m.put("userRole", s.getUserRole());
        m.put("status", s.getStatus());
        m.put("billingCycle", s.getBillingCycle());
        m.put("startDate", s.getStartDate() != null ? s.getStartDate().toString() : null);
        m.put("endDate", s.getEndDate() != null ? s.getEndDate().toString() : null);
        m.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
        m.put("autoRenew", s.isAutoRenew());
        m.put("planId", s.getPlanId());
        m.put("planName", s.getPlanDisplayName());
        return m;
    }
}
