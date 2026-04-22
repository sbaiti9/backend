package esprit.entreprise.controller;

import esprit.entreprise.DTO.InterviewRequest;
import esprit.entreprise.DTO.InterviewResponse;
import esprit.entreprise.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/entreprise/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    /**
     * ✅ Créer un entretien (+ envoi email automatique)
     * POST /entreprise/interviews
     */
    @PostMapping
    public ResponseEntity<?> createInterview(@RequestBody InterviewRequest request) {
        try {
            InterviewResponse interview = interviewService.createInterview(request);
            return ResponseEntity.status(201).body(Map.of(
                    "message", "✅ Entretien planifié et email envoyé!",
                    "interview", interview
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "❌ " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ Récupérer tous les entretiens pour une offre d'emploi
     * GET /entreprise/interviews/job/{jobOfferId}
     */
    @GetMapping("/job/{jobOfferId}")
    public ResponseEntity<?> getInterviewsByJobOffer(@PathVariable Long jobOfferId) {
        try {
            List<InterviewResponse> interviews = interviewService.getInterviewsByJobOffer(jobOfferId);
            return ResponseEntity.ok(Map.of(
                    "interviews", interviews,
                    "total", interviews.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ Récupérer les entretiens à venir pour une offre
     * GET /entreprise/interviews/job/{jobOfferId}/upcoming
     */
    @GetMapping("/job/{jobOfferId}/upcoming")
    public ResponseEntity<?> getUpcomingInterviews(@PathVariable Long jobOfferId) {
        try {
            List<InterviewResponse> interviews = interviewService.getUpcomingInterviews(jobOfferId);
            return ResponseEntity.ok(Map.of(
                    "interviews", interviews,
                    "total", interviews.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ Annuler un entretien (+ email d'annulation)
     * PUT /entreprise/interviews/{id}/cancel
     */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelInterview(@PathVariable Long id) {
        try {
            InterviewResponse interview = interviewService.cancelInterview(id);
            return ResponseEntity.ok(Map.of(
                    "message", "✅ Entretien annulé",
                    "interview", interview
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ Marquer comme terminé
     * PUT /entreprise/interviews/{id}/complete
     */
    @PutMapping("/{id}/complete")
    public ResponseEntity<?> completeInterview(@PathVariable Long id) {
        try {
            InterviewResponse interview = interviewService.completeInterview(id);
            return ResponseEntity.ok(Map.of(
                    "message", "✅ Entretien terminé",
                    "interview", interview
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getInterviewsByUser(@PathVariable Long userId) {
        try {
            List<InterviewResponse> interviews = interviewService.getInterviewsByUser(userId);
            return ResponseEntity.ok(Map.of(
                    "interviews", interviews,
                    "total", interviews.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

















































}