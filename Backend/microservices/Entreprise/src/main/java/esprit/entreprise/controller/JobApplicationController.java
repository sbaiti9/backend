package esprit.entreprise.controller;

import esprit.entreprise.DTO.JobApplicationRequest;
import esprit.entreprise.DTO.JobApplicationResponse;
import esprit.entreprise.entity.ApplicationStatus;
import esprit.entreprise.entity.JobApplication;
import esprit.entreprise.entity.JobOffer;
import esprit.entreprise.repository.JobApplicationRepository;
import esprit.entreprise.repository.JobOfferRepository;
import esprit.entreprise.service.AiService;
import esprit.entreprise.service.JobApplicationService;
import esprit.entreprise.service.InterviewService;
import esprit.entreprise.service.UserServiceClient;
import esprit.entreprise.DTO.InterviewRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/entreprise/applications")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;
    private final JobApplicationRepository jobApplicationRepository;
    private final JobOfferRepository jobOfferRepository;
    private final UserServiceClient userServiceClient;
    private final AiService aiService;
    private final InterviewService interviewService;

    /**
     * ✅ Créer une candidature
     * POST /entreprise/applications/apply
     */
    @PostMapping(value = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createApplication(
            @RequestParam("userId") Long userId,
            @RequestParam("jobOfferId") Long jobOfferId,
            @RequestParam(value = "candidateEmail", required = false) String candidateEmailParam,
            @RequestParam(value = "candidateName", required = false) String candidateNameParam,
            @RequestPart("cv") MultipartFile cv
    ) {
        try {
            if (cv == null || cv.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "CV est obligatoire"));
            }

            System.out.println("📞 Appel Gateway pour récupérer infos utilisateur: " + userId);

            // ✅ Récupérer les infos via Feign Client (si possible)
            Map<String, Object> userInfo = new HashMap<>();
            try {
                userInfo = userServiceClient.getUserById(userId);
                System.out.println("✅ Données brutes reçues: " + userInfo);
            } catch (Exception feignErr) {
                System.err.println("⚠️ Impossible de récupérer les infos utilisateur (Feign). On continue avec des valeurs par défaut.");
                System.err.println("   Détail: " + feignErr.getMessage());
            }

            // ✅ Extraire les données avec gestion des NULL
            String educationLevel = userInfo.get("educationLevel") != null
                    ? (String) userInfo.get("educationLevel")
                    : "Autre";

            String status = userInfo.get("status") != null
                    ? (String) userInfo.get("status")
                    : "NON_ACTIVE";

            // ✅ Gérer average_rating (peut être null ou 0)
            Double averageRating = 0.0;
            Object ratingObj = userInfo.get("average_rating");

            if (ratingObj != null) {
                if (ratingObj instanceof Number) {
                    averageRating = ((Number) ratingObj).doubleValue();
                } else if (ratingObj instanceof String) {
                    try {
                        averageRating = Double.parseDouble((String) ratingObj);
                    } catch (NumberFormatException e) {
                        averageRating = 0.0;
                    }
                }
            }

            String candidateName = userInfo.get("name") != null
                    ? (String) userInfo.get("name")
                    : (candidateNameParam != null && !candidateNameParam.isBlank() ? candidateNameParam : "Utilisateur");

            String candidateEmail = userInfo.get("email") != null
                    ? (String) userInfo.get("email")
                    : (candidateEmailParam != null ? candidateEmailParam : "");

            System.out.println("✅ Infos utilisateur extraites:");
            System.out.println("   educationLevel: " + educationLevel);
            System.out.println("   status: " + status);
            System.out.println("   rating: " + averageRating);
            System.out.println("   name: " + candidateName);
            System.out.println("   email: " + candidateEmail);

            // ✅ Créer la candidature
            JobApplication application = jobApplicationService.createApplication(
                    userId,
                    jobOfferId,
                    educationLevel,
                    status,
                    averageRating,
                    candidateName,
                    candidateEmail,
                    null
            );

            // Attach CV
            jobApplicationService.attachCv(
                    application.getId(),
                    cv.getOriginalFilename(),
                    cv.getContentType(),
                    cv.getBytes()
            );

            System.out.println("✅ Candidature créée: ID=" + application.getId());

            JobOffer offer = jobOfferRepository.findById(jobOfferId).orElse(null);

            // ── AUTO-SHORTLIST if AI score > 60 (no button needed) ──
            try {
                if (offer != null) {
                    Map<String, Object> analysis = aiService.analyzeCv(
                            cv.getBytes(),
                            cv.getOriginalFilename(),
                            cv.getContentType(),
                            offer
                    );

                    int score = 0;
                    Object s = analysis.get("score");
                    if (s instanceof Number) score = ((Number) s).intValue();
                    else if (s instanceof String) {
                        try { score = Integer.parseInt((String) s); } catch (Exception ignored) {}
                    }

                    if (score > 60) {
                        String room = "Skillio-" + application.getId() + "-" + UUID.randomUUID();
                        String meetLink = "https://meet.jit.si/" + room;

                        LocalDateTime interviewDate = LocalDateTime.now().plusDays(1)
                                .withHour(10).withMinute(0).withSecond(0).withNano(0);

                        InterviewRequest req = InterviewRequest.builder()
                                .applicationId(application.getId())
                                .interviewDate(interviewDate)
                                .durationMinutes(30)
                                .type("VIDEO_CALL")
                                .location(meetLink)
                                .notes("Lien Meet: " + meetLink)
                                .build();

                        // creates interview + sends email + sets status SHORTLISTED
                        interviewService.createInterview(req);
                        System.out.println("✅ Auto-shortlist: score=" + score + " applicationId=" + application.getId());
                    }
                }
            } catch (Exception autoErr) {
                // Never block application creation if AI fails
                System.err.println("⚠️ Auto-shortlist skipped: " + autoErr.getMessage());
            }

            JobApplicationResponse response = buildApplicationResponse(application, offer, null);

            return ResponseEntity.status(201).body(Map.of(
                    "message", "✅ Candidature envoyée avec succès!",
                    "application", response
            ));

        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la création de candidature:");
            e.printStackTrace();

            return ResponseEntity.status(500).body(Map.of(
                    "error", "❌ Erreur: " + e.getMessage()
            ));
        }
    }

    /**
     * Backward-compatible endpoint: JSON body (legacy clients).
     * Returns 400 with clear guidance (CV upload required).
     */
    @PostMapping(value = "/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createApplicationJson(@RequestBody JobApplicationRequest request) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "CV est obligatoire. Envoyez multipart/form-data avec userId, jobOfferId et cv."
        ));
    }

    /**
     * ✅ Récupérer les candidatures d'un utilisateur
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserApplications(@PathVariable Long userId) {
        try {
            List<JobApplication> applications = jobApplicationService.getUserApplications(userId);
            List<JobApplicationResponse> responses = applications.stream()
                    .map(app -> buildApplicationResponse(app, app.getJobOffer(), null))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "applications", responses,
                    "total", applications.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ Récupérer les candidatures pour une offre (triées par score)
     */
    @GetMapping("/job/{jobOfferId}")
    public ResponseEntity<?> getJobApplications(@PathVariable Long jobOfferId) {
        try {
            List<JobApplication> applications = jobApplicationService.getJobApplications(jobOfferId);
            List<JobApplicationResponse> responses = applications.stream()
                    .map(app -> buildApplicationResponse(app, app.getJobOffer(), null))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "applications", responses,
                    "total", applications.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ Récupérer les candidatures actives
     */
    @GetMapping("/job/{jobOfferId}/active")
    public ResponseEntity<?> getActiveApplications(@PathVariable Long jobOfferId) {
        try {
            List<JobApplication> applications = jobApplicationService.getActiveApplications(jobOfferId);
            return ResponseEntity.ok(Map.of(
                    "applications", applications.stream()
                            .map(app -> buildApplicationResponse(app, app.getJobOffer(), null))
                            .collect(Collectors.toList())
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ Récupérer les meilleures candidatures (score >= 70)
     */
    @GetMapping("/job/{jobOfferId}/top-qualified")
    public ResponseEntity<?> getTopQualifiedApplications(@PathVariable Long jobOfferId) {
        try {
            List<JobApplication> applications = jobApplicationService.getTopQualifiedApplications(jobOfferId);
            return ResponseEntity.ok(Map.of(
                    "applications", applications.stream()
                            .map(app -> buildApplicationResponse(app, app.getJobOffer(), null))
                            .collect(Collectors.toList())
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ Détails d'une candidature
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getApplication(@PathVariable Long id) {
        try {
            JobApplication application = jobApplicationRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Candidature non trouvée"));

            JobApplicationResponse response = buildApplicationResponse(application, application.getJobOffer(), null);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 📥 Télécharger le CV d'une candidature
     * GET /entreprise/applications/{id}/cv
     */
    @GetMapping("/{id}/cv")
    public ResponseEntity<?> downloadCv(@PathVariable Long id) {
        try {
            Optional<JobApplication> opt = jobApplicationRepository.findById(id);
            if (opt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Candidature non trouvée"));
            }
            JobApplication app = opt.get();
            byte[] data = app.getCvData();
            if (data == null || data.length == 0) {
                return ResponseEntity.status(404).body(Map.of("error", "CV introuvable"));
            }

            String fileName = (app.getCvFileName() == null || app.getCvFileName().isBlank())
                    ? ("cv-application-" + id + ".pdf")
                    : app.getCvFileName();
            String contentType = (app.getCvContentType() == null || app.getCvContentType().isBlank())
                    ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                    : app.getCvContentType();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentLength(data.length);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName.replace("\"", "") + "\"");

            return new ResponseEntity<>(data, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "❌ Erreur: " + e.getMessage()));
        }
    }

    /**
     * ✅ Accepter une candidature
     */
    @PutMapping("/{id}/accept")
    public ResponseEntity<?> acceptApplication(@PathVariable Long id) {
        try {
            JobApplication application = jobApplicationService.acceptApplication(id);
            return ResponseEntity.ok(Map.of(
                    "message", "✅ Candidature acceptée",
                    "application", application
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ Rejeter une candidature
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectApplication(@PathVariable Long id) {
        try {
            JobApplication application = jobApplicationService.rejectApplication(id);
            return ResponseEntity.ok(Map.of(
                    "message", "✅ Candidature rejetée",
                    "application", application
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ Présélectionner une candidature
     */
    @PutMapping("/{id}/shortlist")
    public ResponseEntity<?> shortlistApplication(@PathVariable Long id) {
        try {
            JobApplication application = jobApplicationService.shortlistApplication(id);
            return ResponseEntity.ok(Map.of(
                    "message", "✅ Candidature présélectionnée",
                    "application", application
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ Mettre à jour le statut
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateApplicationStatus(@PathVariable Long id,
                                                     @RequestParam String status) {
        try {
            JobApplication application = jobApplicationService.updateApplicationStatus(id,
                    ApplicationStatus.valueOf(status));
            return ResponseEntity.ok(Map.of(
                    "message", "✅ Statut mis à jour",
                    "application", application
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ Retirer une candidature
     */
    @DeleteMapping("/{id}/withdraw")
    public ResponseEntity<?> withdrawApplication(@PathVariable Long id) {
        try {
            jobApplicationService.withdrawApplication(id);
            return ResponseEntity.ok(Map.of("message", "✅ Candidature retirée"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ Supprimer une candidature (admin)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteApplication(@PathVariable Long id) {
        try {
            jobApplicationService.deleteApplication(id);
            return ResponseEntity.ok(Map.of("message", "✅ Candidature supprimée"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ Statistiques de candidatures
     */
    @GetMapping("/job/{jobOfferId}/stats")
    public ResponseEntity<?> getApplicationStats(@PathVariable Long jobOfferId) {
        try {
            Map<String, Long> stats = jobApplicationService.getApplicationStats(jobOfferId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ Vérifier si l'utilisateur a déjà postulé
     */
    @GetMapping("/check/{userId}/{jobOfferId}")
    public ResponseEntity<?> hasAlreadyApplied(@PathVariable Long userId,
                                               @PathVariable Long jobOfferId) {
        try {
            boolean hasApplied = jobApplicationService.hasAlreadyApplied(userId, jobOfferId);
            return ResponseEntity.ok(Map.of(
                    "hasApplied", hasApplied,
                    "message", hasApplied ? "❌ Vous avez déjà postulé" : "✅ Vous pouvez postuler"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ Construire la réponse
     */
    private JobApplicationResponse buildApplicationResponse(JobApplication app, JobOffer offer, Object userInfo) {
        return JobApplicationResponse.builder()
                .id(app.getId())
                .userId(app.getUserId())
                .jobOfferId(app.getJobOffer().getId())
                .jobTitle(app.getJobOffer().getTitle())
                .companyName(app.getJobOffer().getCompany() != null ? app.getJobOffer().getCompany().getName() : "N/A")
                .candidateName(app.getCandidateName())
                .candidateEmail(app.getCandidateEmail())
                .educationLevel(app.getEducationLevel())
                .candidateStatus(app.getCandidateStatus())
                .averageRating(app.getCandidateAverageRating())
                .profession(null)
                .matchingScore(app.getMatchingScore())
                .educationLevelScore(app.getEducationLevelScore())
                .ratingScore(app.getRatingScore())
                .statusScore(app.getStatusScore())
                .applicationStatus(app.getStatus().toString())
                .coverLetter(app.getCoverLetter())
                .cvFileName(app.getCvFileName())
                .hasCv(app.getCvData() != null && app.getCvData().length > 0)
                .applicationDate(app.getApplicationDate())           // ✅ Sans .toString()
                .updatedDate(app.getUpdatedDate())
                .build();
    }
}