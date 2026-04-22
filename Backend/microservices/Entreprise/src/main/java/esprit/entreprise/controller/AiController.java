package esprit.entreprise.controller;

import esprit.entreprise.entity.JobOffer;
import esprit.entreprise.entity.JobApplication;
import esprit.entreprise.entity.Company;
import esprit.entreprise.repository.JobApplicationRepository;
import esprit.entreprise.repository.CompanyRepository;
import esprit.entreprise.repository.JobOfferRepository;
import esprit.entreprise.repository.SupportMessageRepository;
import org.springframework.data.domain.Sort;
import esprit.entreprise.service.AiService;
import esprit.entreprise.service.JobOfferService;
import esprit.entreprise.service.JobApplicationService;
import esprit.entreprise.service.InterviewService;
import esprit.entreprise.DTO.InterviewRequest;
import esprit.entreprise.DTO.InterviewResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/entreprise/ai")
public class
AiController {

    private final AiService aiService;
    private final JobOfferService jobOfferService;
    private final JobApplicationRepository jobApplicationRepository;
    private final JobApplicationService jobApplicationService;
    private final InterviewService interviewService;
    private final CompanyRepository companyRepository;
    private final JobOfferRepository jobOfferRepository;
    private final SupportMessageRepository supportMessageRepository;

    public AiController(
            AiService aiService,
            JobOfferService jobOfferService,
            JobApplicationRepository jobApplicationRepository,
            JobApplicationService jobApplicationService,
            InterviewService interviewService,
            CompanyRepository companyRepository,
            JobOfferRepository jobOfferRepository,
            SupportMessageRepository supportMessageRepository
    ) {
        this.aiService = aiService;
        this.jobOfferService = jobOfferService;
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobApplicationService = jobApplicationService;
        this.interviewService = interviewService;
        this.companyRepository = companyRepository;
        this.jobOfferRepository = jobOfferRepository;
        this.supportMessageRepository = supportMessageRepository;
    }

    /**
     * POST /entreprise/ai/generate-job-offer
     * Body: { "title": "Angular Developer" }
     * Returns: JSON with generated job offer fields
     */
    @PostMapping("/generate-job-offer")
    public ResponseEntity<Map<String, Object>> generateJobOffer(@RequestBody Map<String, String> request) {
        String title = request.get("title");

        if (title == null || title.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Le titre du poste est requis"));
        }

        try {
            Map<String, Object> generated = aiService.generateJobOffer(title.trim());
            return ResponseEntity.ok(generated);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Erreur lors de la génération: " + e.getMessage())
            );
        }
    }

    /**
     * POST /entreprise/ai/job-offers/{id}/questions
     * Returns: { "questions": [ { "question": "...", "why": "..." }, ... ] }
     */
    @PostMapping("/job-offers/{id}/questions")
    public ResponseEntity<Map<String, Object>> generateQuestionsForOffer(@PathVariable("id") Long id) {
        JobOffer offer = jobOfferService.findById(id).orElse(null);
        if (offer == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Offre introuvable"));
        }

        try {
            Map<String, Object> out = aiService.generateQuestionsForOffer(offer);
            Object questions = out.get("questions");
            if (!(questions instanceof List)) {
                Map<String, Object> fallback = new HashMap<>();
                fallback.put("questions", List.of());
                return ResponseEntity.ok(fallback);
            }
            return ResponseEntity.ok(out);
        } catch (IllegalStateException e) {
            // e.g. Groq not configured
            return ResponseEntity.badRequest().body(
                    Map.of("error", e.getMessage())
            );
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Erreur lors de la génération: " + e.getMessage())
            );
        }
    }

    /**
     * POST /entreprise/ai/job-offers/{id}/practice-quiz
     * QCM d'entraînement (Groq) basé sur le titre et le contexte de l'offre.
     * Returns: { "questions": [ { "question", "options", "correctIndex", "explanation" }, ... ] }
     */
    @PostMapping("/job-offers/{id}/practice-quiz")
    public ResponseEntity<Map<String, Object>> generatePracticeQuizForOffer(@PathVariable("id") Long id) {
        JobOffer offer = jobOfferService.findById(id).orElse(null);
        if (offer == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Offre introuvable"));
        }
        try {
            Map<String, Object> out = aiService.generatePracticeQuizForOffer(offer);
            Object questions = out.get("questions");
            if (!(questions instanceof List)) {
                return ResponseEntity.ok(Map.of("questions", List.of()));
            }
            return ResponseEntity.ok(out);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Erreur lors de la génération du quiz: " + e.getMessage())
            );
        }
    }

    /**
     * POST /entreprise/ai/job-offers/{id}/translate
     * Retourne l'offre traduite en français et en anglais (Groq) : {@code fr}, {@code en}.
     */
    @PostMapping("/job-offers/{id}/translate")
    public ResponseEntity<Map<String, Object>> translateJobOffer(@PathVariable("id") Long id) {
        JobOffer offer = jobOfferService.findById(id).orElse(null);
        if (offer == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Offre introuvable"));
        }
        try {
            Map<String, Object> out = aiService.translateJobOffer(offer);
            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Erreur lors de la traduction: " + e.getMessage())
            );
        }
    }

    /**
     * POST /entreprise/ai/companies/search
     * Body: { "text": "..." }
     * Returns: { "query": "...", "industry": ""|"tech"|"finance"|"education"|"healthcare" }
     */
    @PostMapping("/companies/search")
    public ResponseEntity<Map<String, Object>> parseCompanySearch(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        try {
            Map<String, Object> out = aiService.parseCompanySearch(text);
            return ResponseEntity.ok(out);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Erreur lors de l'analyse IA: " + e.getMessage())
            );
        }
    }

    /**
     * POST /entreprise/ai/job-offers/search
     * Body: { "text": "..." }
     * Returns: { "query": "...", "contractTypes": ["CDI","CDD","Stage"], "remote": "all"|"Complet"|"Partiel"|"Non" }
     */
    @PostMapping("/job-offers/search")
    public ResponseEntity<Map<String, Object>> parseJobOfferSearch(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        try {
            Map<String, Object> out = aiService.parseJobOfferSearch(text);
            return ResponseEntity.ok(out);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Erreur lors de l'analyse IA: " + e.getMessage())
            );
        }
    }

    /**
     * POST /entreprise/ai/cv/student-assist
     * Body: { "notes": "...", "targetRole": "..." } (targetRole optionnel)
     * Aide l'étudiant à formuler son CV (Groq).
     */
    @PostMapping("/cv/student-assist")
    public ResponseEntity<Map<String, Object>> studentCvAssist(@RequestBody Map<String, String> request) {
        String notes = request != null && request.get("notes") != null ? request.get("notes") : "";
        String targetRole = request != null && request.get("targetRole") != null ? request.get("targetRole") : "";
        try {
            Map<String, Object> out = aiService.assistStudentCv(notes, targetRole);
            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Erreur assistant CV: " + e.getMessage())
            );
        }
    }

    /**
     * POST /entreprise/ai/cover-letter/student-assist
     * Body: { "notes", "targetRole", "companyName" } (rôles optionnels)
     */
    @PostMapping("/cover-letter/student-assist")
    public ResponseEntity<Map<String, Object>> studentCoverLetterAssist(@RequestBody Map<String, String> request) {
        String notes = request != null && request.get("notes") != null ? request.get("notes") : "";
        String targetRole = request != null && request.get("targetRole") != null ? request.get("targetRole") : "";
        String companyName = request != null && request.get("companyName") != null ? request.get("companyName") : "";
        try {
            Map<String, Object> out = aiService.assistStudentCoverLetter(notes, targetRole, companyName);
            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Erreur lettre de motivation: " + e.getMessage())
            );
        }
    }

    /**
     * POST /entreprise/ai/interview/student-assist
     * Body: { "notes", "targetRole" }
     */
    @PostMapping("/interview/student-assist")
    public ResponseEntity<Map<String, Object>> studentInterviewAssist(@RequestBody Map<String, String> request) {
        String notes = request != null && request.get("notes") != null ? request.get("notes") : "";
        String targetRole = request != null && request.get("targetRole") != null ? request.get("targetRole") : "";
        try {
            Map<String, Object> out = aiService.assistStudentInterviewPrep(notes, targetRole);
            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Erreur préparation entretien: " + e.getMessage())
            );
        }
    }

    /**
     * POST /entreprise/ai/applications/{id}/cv-analysis
     * Returns: { "score":0-100, "summary":"...", "strengths":[...], "gaps":[...], "recommendations":[...] }
     */
    @PostMapping("/applications/{id}/cv-analysis")
    public ResponseEntity<Map<String, Object>> analyzeCv(@PathVariable("id") Long id) {
        JobApplication app = jobApplicationRepository.findById(id).orElse(null);
        if (app == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Candidature introuvable"));
        }
        if (app.getCvData() == null || app.getCvData().length == 0) {
            return ResponseEntity.status(404).body(Map.of("error", "CV introuvable"));
        }
        JobOffer offer = app.getJobOffer();
        if (offer == null) {
            return ResponseEntity.status(400).body(Map.of("error", "Offre liée introuvable"));
        }
        try {
            Map<String, Object> out = aiService.analyzeCv(
                    app.getCvData(),
                    app.getCvFileName(),
                    app.getCvContentType(),
                    offer
            );
            return ResponseEntity.ok(out);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Erreur lors de l'analyse CV: " + e.getMessage())
            );
        }
    }

    /**
     * POST /entreprise/ai/applications/{id}/cv-analysis/auto
     * If score > 60: accept application + schedule interview (Meet link) + send email.
     */
    @PostMapping("/applications/{id}/cv-analysis/auto")
    public ResponseEntity<Map<String, Object>> analyzeCvAndAutoAct(@PathVariable("id") Long id) {
        JobApplication app = jobApplicationRepository.findById(id).orElse(null);
        if (app == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Candidature introuvable"));
        }
        if (app.getCvData() == null || app.getCvData().length == 0) {
            return ResponseEntity.status(404).body(Map.of("error", "CV introuvable"));
        }
        JobOffer offer = app.getJobOffer();
        if (offer == null) {
            return ResponseEntity.status(400).body(Map.of("error", "Offre liée introuvable"));
        }

        try {
            Map<String, Object> analysis = aiService.analyzeCv(
                    app.getCvData(),
                    app.getCvFileName(),
                    app.getCvContentType(),
                    offer
            );

            int score = 0;
            Object s = analysis.get("score");
            if (s instanceof Number) score = ((Number) s).intValue();
            else if (s instanceof String) {
                try { score = Integer.parseInt((String) s); } catch (Exception ignored) {}
            }

            Map<String, Object> out = new HashMap<>(analysis);
            out.put("threshold", 60);
            out.put("autoAccepted", false);
            out.put("interviewCreated", false);

            if (score > 60) {
                // 1) Accept application
                jobApplicationService.acceptApplication(app.getId());
                out.put("autoAccepted", true);

                // 2) Schedule interview with a Meet-like link (Jitsi) and send email
                String room = "Skillio-" + app.getId() + "-" + UUID.randomUUID();
                String meetLink = "https://meet.jit.si/" + room;

                LocalDateTime interviewDate = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
                InterviewRequest req = InterviewRequest.builder()
                        .applicationId(app.getId())
                        .interviewDate(interviewDate)
                        .durationMinutes(30)
                        .type("VIDEO_CALL")
                        .location(meetLink)
                        .notes("Lien Meet: " + meetLink)
                        .build();

                try {
                    InterviewResponse interview = interviewService.createInterview(req);
                    out.put("interviewCreated", true);
                    out.put("meetLink", meetLink);
                    out.put("interview", interview);
                } catch (Exception ivErr) {
                    out.put("interviewError", ivErr.getMessage());
                }
            }

            return ResponseEntity.ok(out);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Erreur lors de l'analyse CV: " + e.getMessage())
            );
        }
    }

    /**
     * POST /entreprise/ai/companies/{id}/offer-suggestions
     * Returns: { "suggestions": [ ... ] }
     */
    @PostMapping("/companies/{id}/offer-suggestions")
    public ResponseEntity<Map<String, Object>> offerSuggestions(@PathVariable("id") Long companyId) {
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Entreprise introuvable"));
        }
        try {
            List<JobOffer> offers = jobOfferRepository.findByCompany_Id(companyId);
            Map<String, Object> out = aiService.suggestNextOffers(company, offers);
            return ResponseEntity.ok(out);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Erreur lors des prédictions IA: " + e.getMessage())
            );
        }
    }

    /**
     * POST /entreprise/ai/companies/{id}/offers/ai-decline-risk
     * Returns: { "risks": [ {title, risk, why, aiImpact, mitigation[], alternatives[]} ] }
     */
    @PostMapping("/companies/{id}/offers/ai-decline-risk")
    public ResponseEntity<Map<String, Object>> offersAiDeclineRisk(@PathVariable("id") Long companyId) {
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Entreprise introuvable"));
        }
        try {
            List<JobOffer> offers = jobOfferRepository.findByCompany_Id(companyId);
            Map<String, Object> out = aiService.predictOffersAiDecline(company, offers);
            return ResponseEntity.ok(out);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Erreur lors de l'analyse IA: " + e.getMessage())
            );
        }
    }

    /**
     * POST /entreprise/ai/companies/{id}/activation-emoji
     * Dashboard entreprise : état d’activation sur Skillio sous forme d’emojis + texte court (Groq).
     */
    @PostMapping("/companies/{id}/activation-emoji")
    public ResponseEntity<Map<String, Object>> companyActivationEmoji(@PathVariable("id") Long companyId) {
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Entreprise introuvable"));
        }
        try {
            List<JobOffer> offers = jobOfferRepository.findByCompany_Id(companyId);
            long totalOffers = offers.size();
            long activeOffers = offers.stream().filter(j -> Boolean.TRUE.equals(j.getIsActive())).count();

            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("companyName", company.getName());
            snap.put("industry", company.getIndustry() != null ? company.getIndustry() : "");
            snap.put("location", company.getLocation() != null ? company.getLocation() : "");
            snap.put("hasWebsite", company.getWebsite() != null && !company.getWebsite().isBlank());
            snap.put("hasPhone", company.getPhone() != null && !company.getPhone().isBlank());
            snap.put("hasLogoUrl", company.getLogoUrl() != null && !company.getLogoUrl().isBlank());
            snap.put("totalJobOffers", totalOffers);
            snap.put("activeJobOffers", activeOffers);

            Map<String, Object> out = aiService.companyActivationEmojiInsight(snap);
            return ResponseEntity.ok(out);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Erreur lors de l’analyse IA: " + e.getMessage())
            );
        }
    }

    /**
     * POST /entreprise/ai/admin/company-platform-fit
     * Analyse IA (admin) : entreprises alignées avec Skillio vs à améliorer, avec propositions plateforme + entreprise.
     */
    @PostMapping("/admin/company-platform-fit")
    public ResponseEntity<Map<String, Object>> adminCompanyPlatformFit() {
        List<Company> companies = companyRepository.findAll(Sort.by(Sort.Order.asc("id")));
        if (companies.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("summary", "Aucune entreprise enregistrée sur la plateforme.");
            empty.put("bienServies", List.of());
            empty.put("aAmeliorer", List.of());
            empty.put("truncated", false);
            empty.put("inputCompanyCount", 0);
            return ResponseEntity.ok(empty);
        }

        final int maxCompanies = 80;
        boolean truncated = companies.size() > maxCompanies;
        List<Company> slice = truncated ? companies.subList(0, maxCompanies) : companies;

        Map<Long, long[]> offersByCompany = new HashMap<>();
        for (Object[] row : jobOfferRepository.countOffersAggregatedByCompanyId()) {
            Long cid = (Long) row[0];
            offersByCompany.put(cid, new long[]{asLong(row[1]), asLong(row[2])});
        }
        Map<Long, Long> applicationsByCompany = new HashMap<>();
        for (Object[] row : jobApplicationRepository.countApplicationsByCompanyId()) {
            applicationsByCompany.put((Long) row[0], asLong(row[1]));
        }
        Map<Long, Long> supportTotalByCompany = new HashMap<>();
        for (Object[] row : supportMessageRepository.countMessagesByCompanyId()) {
            supportTotalByCompany.put((Long) row[0], asLong(row[1]));
        }
        Map<Long, Long> supportEnterpriseByCompany = new HashMap<>();
        for (Object[] row : supportMessageRepository.countEnterpriseMessagesByCompanyId()) {
            supportEnterpriseByCompany.put((Long) row[0], asLong(row[1]));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Company c : slice) {
            long[] o = offersByCompany.getOrDefault(c.getId(), new long[]{0L, 0L});
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("companyId", c.getId());
            m.put("companyName", c.getName());
            m.put("industry", c.getIndustry());
            m.put("location", c.getLocation());
            m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
            m.put("totalJobOffers", o[0]);
            m.put("activeJobOffers", o[1]);
            m.put("totalApplications", applicationsByCompany.getOrDefault(c.getId(), 0L));
            m.put("supportMessagesTotal", supportTotalByCompany.getOrDefault(c.getId(), 0L));
            m.put("supportMessagesFromEnterprise", supportEnterpriseByCompany.getOrDefault(c.getId(), 0L));
            rows.add(m);
        }

        try {
            Map<String, Object> out = aiService.analyzeAdminCompanyPlatformFit(rows);
            out.put("truncated", truncated);
            out.put("inputCompanyCount", slice.size());
            if (truncated) {
                out.put("warning", "Analyse limitée aux " + maxCompanies + " premières entreprises (tri par id).");
            }
            return ResponseEntity.ok(out);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Erreur lors de l'analyse IA: " + e.getMessage())
            );
        }
    }

    private static long asLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        return 0L;
    }
}