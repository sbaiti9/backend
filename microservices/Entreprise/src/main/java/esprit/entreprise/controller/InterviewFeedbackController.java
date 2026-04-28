package esprit.entreprise.controller;

import esprit.entreprise.DTO.InterviewFeedbackSubmitRequest;
import esprit.entreprise.service.InterviewFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/entreprise/interview-feedback")
@RequiredArgsConstructor
public class InterviewFeedbackController {

    private final InterviewFeedbackService interviewFeedbackService;

    /**
     * POST /entreprise/interview-feedback
     * Candidat : soumet un avis post-entretien (anonyme côté entreprise).
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> submit(@RequestBody InterviewFeedbackSubmitRequest body) {
        try {
            return ResponseEntity.ok(interviewFeedbackService.submit(body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /entreprise/interview-feedback/company/{companyId}/stats
     * Entreprise : statistiques agrégées (seuil d’anonymat).
     */
    @GetMapping("/company/{companyId}/stats")
    public ResponseEntity<Map<String, Object>> companyStats(@PathVariable Long companyId) {
        try {
            return ResponseEntity.ok(interviewFeedbackService.statsForCompany(companyId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /entreprise/interview-feedback/admin/stats
     * Admin : vue globale + par entreprise.
     */
    @GetMapping("/admin/stats")
    public ResponseEntity<Map<String, Object>> adminStats() {
        try {
            return ResponseEntity.ok(interviewFeedbackService.adminStats());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
