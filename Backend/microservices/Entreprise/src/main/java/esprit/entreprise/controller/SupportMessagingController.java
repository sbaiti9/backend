package esprit.entreprise.controller;

import esprit.entreprise.DTO.SupportMessagePostRequest;
import esprit.entreprise.entity.SupportMessage;
import esprit.entreprise.service.SupportMessagingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Messagerie support admin ↔ entreprise (un fil par {@code companyId}).
 */
@RestController
@RequestMapping("/entreprise/support-messages")
public class SupportMessagingController {

    private final SupportMessagingService supportMessagingService;

    public SupportMessagingController(SupportMessagingService supportMessagingService) {
        this.supportMessagingService = supportMessagingService;
    }

    /**
     * GET /entreprise/support-messages/company/{companyId}
     */
    @GetMapping("/company/{companyId}")
    public ResponseEntity<List<SupportMessage>> listByCompany(@PathVariable("companyId") Long companyId) {
        return ResponseEntity.ok(supportMessagingService.listMessages(companyId));
    }

    /**
     * POST /entreprise/support-messages
     * Body: { "companyId", "senderRole": "ADMIN"|"ENTREPRISE", "senderUserId", "body" }
     */
    @PostMapping
    public ResponseEntity<?> post(@RequestBody SupportMessagePostRequest request) {
        try {
            SupportMessage saved = supportMessagingService.post(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /entreprise/support-messages/admin/unread-count
     * Nombre de messages entreprise non lus par l’admin (badge menu).
     */
    @GetMapping("/admin/unread-count")
    public Map<String, Long> adminUnreadCount() {
        return Map.of("count", supportMessagingService.countUnreadFromEnterpriseForAdmin());
    }

    /**
     * POST /entreprise/support-messages/company/{companyId}/mark-read-by-admin
     * Marque comme lus les messages entreprise de ce fil (quand l’admin ouvre la conversation).
     */
    @PostMapping("/company/{companyId}/mark-read-by-admin")
    public ResponseEntity<Map<String, Object>> markReadByAdmin(@PathVariable("companyId") Long companyId) {
        int updated = supportMessagingService.markReadByAdminForCompany(companyId);
        return ResponseEntity.ok(Map.of("updated", updated));
    }
}
