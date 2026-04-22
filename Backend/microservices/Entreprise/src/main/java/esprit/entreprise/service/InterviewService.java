package esprit.entreprise.service;

import esprit.entreprise.DTO.InterviewRequest;
import esprit.entreprise.DTO.InterviewResponse;
import esprit.entreprise.entity.*;
import esprit.entreprise.repository.InterviewCandidateFeedbackRepository;
import esprit.entreprise.repository.InterviewRepository;
import esprit.entreprise.repository.JobApplicationRepository;
import esprit.entreprise.repository.JobOfferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final JobOfferRepository jobOfferRepository;
    private final InterviewCandidateFeedbackRepository interviewCandidateFeedbackRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * ✅ Créer un entretien et envoyer un email au candidat
     */
    public InterviewResponse createInterview(InterviewRequest request) {

        // 1. Récupérer la candidature
        JobApplication application = jobApplicationRepository.findById(request.getApplicationId())
                .orElseThrow(() -> new RuntimeException("Candidature non trouvée: " + request.getApplicationId()));

        // 2. Vérifier qu'il n'y a pas déjà un entretien
        if (interviewRepository.existsByApplicationId(request.getApplicationId())) {
            throw new RuntimeException("Un entretien existe déjà pour cette candidature");
        }

        // 3. Récupérer l'offre d'emploi
        JobOffer jobOffer = application.getJobOffer();

        // 4. Mettre à jour le statut de la candidature
        // If already ACCEPTED (auto-flow), do not downgrade it to SHORTLISTED.
        if (application.getStatus() != ApplicationStatus.ACCEPTED) {
            application.setStatus(ApplicationStatus.SHORTLISTED);
            jobApplicationRepository.save(application);
        }

        // 5. Créer l'entretien
        Interview interview = Interview.builder()
                .application(application)
                .jobOffer(jobOffer)
                .candidateUserId(application.getUserId())
                .candidateName(application.getCandidateName())
                .candidateEmail(application.getCandidateEmail())
                .interviewDate(request.getInterviewDate())
                .durationMinutes(request.getDurationMinutes() != null ? request.getDurationMinutes() : 30)
                .location(request.getLocation())
                .type(request.getType() != null
                        ? Interview.InterviewType.valueOf(request.getType())
                        : Interview.InterviewType.ON_SITE)
                .notes(request.getNotes())
                .status(Interview.InterviewStatus.SCHEDULED)
                .emailSent(false)
                .build();

        Interview saved = interviewRepository.save(interview);

        // 6. Envoyer l'email au candidat
        try {
            sendInterviewEmail(saved, jobOffer);
            saved.setEmailSent(true);
            interviewRepository.save(saved);
            System.out.println("✅ Email d'entretien envoyé à: " + saved.getCandidateEmail());
        } catch (Exception e) {
            System.err.println("❌ Échec de l'envoi d'email: " + e.getMessage());
            // L'entretien est créé même si l'email échoue
        }

        return toResponse(saved);
    }

    /**
     * ✅ Récupérer les entretiens pour une offre d'emploi
     */
    public List<InterviewResponse> getInterviewsByJobOffer(Long jobOfferId) {
        return interviewRepository.findByJobOfferIdOrderByInterviewDateAsc(jobOfferId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * ✅ Récupérer les entretiens à venir pour une offre
     */
    public List<InterviewResponse> getUpcomingInterviews(Long jobOfferId) {
        return interviewRepository.findByJobOfferIdAndInterviewDateAfterOrderByInterviewDateAsc(
                        jobOfferId, LocalDateTime.now())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * ✅ Annuler un entretien
     */
    public InterviewResponse cancelInterview(Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new RuntimeException("Entretien non trouvé"));

        interview.setStatus(Interview.InterviewStatus.CANCELLED);
        Interview saved = interviewRepository.save(interview);

        // Envoyer email d'annulation
        try {
            sendCancellationEmail(saved);
        } catch (Exception e) {
            System.err.println("❌ Échec envoi email annulation: " + e.getMessage());
        }

        return toResponse(saved);
    }

    /**
     * ✅ Marquer un entretien comme terminé
     */
    public InterviewResponse completeInterview(Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new RuntimeException("Entretien non trouvé"));

        interview.setStatus(Interview.InterviewStatus.COMPLETED);
        return toResponse(interviewRepository.save(interview));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 📧 EMAIL SENDING
    // ══════════════════════════════════════════════════════════════════════════

    private void sendInterviewEmail(Interview interview, JobOffer jobOffer) throws Exception {
        if (interview.getCandidateEmail() == null || interview.getCandidateEmail().isEmpty()) {
            System.out.println("⚠️ Pas d'email candidat, envoi ignoré");
            return;
        }

        String companyName = jobOffer.getCompany() != null ? jobOffer.getCompany().getName() : "L'entreprise";
        String dateStr = interview.getInterviewDate().format(DATE_FORMAT);
        String timeStr = interview.getInterviewDate().format(TIME_FORMAT);
        String typeLabel = getInterviewTypeLabel(interview.getType());

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        if (fromEmail != null && !fromEmail.isBlank()) {
            helper.setFrom(fromEmail);
        }
        helper.setTo(interview.getCandidateEmail());
        helper.setSubject("📅 Invitation à un entretien - " + jobOffer.getTitle() + " | " + companyName);

        String htmlContent = buildInterviewEmailHtml(
                interview.getCandidateName(),
                companyName,
                jobOffer.getTitle(),
                dateStr,
                timeStr,
                interview.getDurationMinutes(),
                typeLabel,
                interview.getLocation(),
                interview.getNotes()
        );

        helper.setText(htmlContent, true);
        mailSender.send(message);
    }

    private void sendCancellationEmail(Interview interview) throws Exception {
        if (interview.getCandidateEmail() == null || interview.getCandidateEmail().isEmpty()) return;

        String companyName = interview.getJobOffer().getCompany() != null
                ? interview.getJobOffer().getCompany().getName() : "L'entreprise";

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(interview.getCandidateEmail());
        helper.setSubject("❌ Entretien annulé - " + interview.getJobOffer().getTitle());

        String html = "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;'>"
                + "<div style='background:#ef4444;color:white;padding:24px;border-radius:12px 12px 0 0;text-align:center;'>"
                + "<h2 style='margin:0;'>Entretien Annulé</h2></div>"
                + "<div style='padding:24px;background:#fff;border:1px solid #e5e7eb;border-radius:0 0 12px 12px;'>"
                + "<p>Bonjour <strong>" + interview.getCandidateName() + "</strong>,</p>"
                + "<p>Nous vous informons que l'entretien prévu pour le poste de <strong>"
                + interview.getJobOffer().getTitle() + "</strong> chez <strong>" + companyName
                + "</strong> a été <span style='color:#ef4444;font-weight:bold;'>annulé</span>.</p>"
                + "<p>Nous vous contacterons prochainement pour convenir d'une nouvelle date.</p>"
                + "<p>Cordialement,<br><strong>" + companyName + "</strong></p>"
                + "</div></div>";

        helper.setText(html, true);
        mailSender.send(message);
    }

    /**
     * ✅ Template HTML pour l'email d'invitation à l'entretien
     */
    private String buildInterviewEmailHtml(String candidateName, String companyName,
                                           String jobTitle, String date, String time,
                                           int duration, String type, String location, String notes) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;'>");

        // Header
        sb.append("<div style='background:linear-gradient(135deg,#7c3aed,#6d28d9);color:white;padding:32px 24px;border-radius:12px 12px 0 0;text-align:center;'>");
        sb.append("<h1 style='margin:0 0 8px;font-size:24px;'>📅 Invitation à un Entretien</h1>");
        sb.append("<p style='margin:0;opacity:0.9;'>").append(companyName).append("</p>");
        sb.append("</div>");

        // Body
        sb.append("<div style='padding:28px 24px;background:#ffffff;border:1px solid #e5e7eb;'>");

        // Greeting
        sb.append("<p style='font-size:16px;color:#1f2937;'>Bonjour <strong>").append(candidateName).append("</strong>,</p>");
        sb.append("<p style='color:#4b5563;line-height:1.6;'>Nous avons le plaisir de vous informer que votre candidature pour le poste de <strong style='color:#7c3aed;'>")
                .append(jobTitle).append("</strong> a retenu notre attention. Nous souhaitons vous rencontrer lors d'un entretien.</p>");

        // Details Box
        sb.append("<div style='background:#f9fafb;border:1px solid #e5e7eb;border-left:4px solid #7c3aed;border-radius:8px;padding:20px;margin:20px 0;'>");
        sb.append("<h3 style='margin:0 0 12px;color:#1f2937;font-size:16px;'>📋 Détails de l'entretien</h3>");
        sb.append("<table style='width:100%;border-collapse:collapse;'>");
        sb.append("<tr><td style='padding:8px 0;color:#6b7280;width:140px;'>📅 Date</td><td style='padding:8px 0;font-weight:600;color:#1f2937;'>").append(date).append("</td></tr>");
        sb.append("<tr><td style='padding:8px 0;color:#6b7280;'>⏰ Heure</td><td style='padding:8px 0;font-weight:600;color:#1f2937;'>").append(time).append("</td></tr>");
        sb.append("<tr><td style='padding:8px 0;color:#6b7280;'>⏱️ Durée</td><td style='padding:8px 0;font-weight:600;color:#1f2937;'>").append(duration).append(" minutes</td></tr>");
        sb.append("<tr><td style='padding:8px 0;color:#6b7280;'>🏢 Type</td><td style='padding:8px 0;font-weight:600;color:#1f2937;'>").append(type).append("</td></tr>");
        if (location != null && !location.isEmpty()) {
            sb.append("<tr><td style='padding:8px 0;color:#6b7280;'>📍 Lieu</td><td style='padding:8px 0;font-weight:600;color:#1f2937;'>").append(location).append("</td></tr>");
        }
        sb.append("</table>");
        sb.append("</div>");

        // Notes
        if (notes != null && !notes.isEmpty()) {
            sb.append("<div style='background:#fffbeb;border:1px solid #fde68a;border-radius:8px;padding:14px;margin:16px 0;'>");
            sb.append("<p style='margin:0;color:#92400e;font-size:14px;'><strong>📝 Note:</strong> ").append(notes).append("</p>");
            sb.append("</div>");
        }

        // CTA
        sb.append("<p style='color:#4b5563;line-height:1.6;margin-top:20px;'>Merci de confirmer votre présence en répondant à cet email. En cas d'empêchement, veuillez nous prévenir dans les plus brefs délais.</p>");

        // Footer
        sb.append("<p style='color:#4b5563;margin-top:24px;'>Cordialement,<br><strong style='color:#7c3aed;'>").append(companyName).append("</strong></p>");
        sb.append("</div>");

        // Bottom bar
        sb.append("<div style='background:#f3f4f6;padding:16px 24px;border-radius:0 0 12px 12px;text-align:center;border:1px solid #e5e7eb;border-top:none;'>");
        sb.append("<p style='margin:0;font-size:12px;color:#9ca3af;'>Cet email a été envoyé automatiquement. Merci de ne pas y répondre directement.</p>");
        sb.append("</div>");

        sb.append("</div>");
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private String getInterviewTypeLabel(Interview.InterviewType type) {
        if (type == null) return "Présentiel";
        switch (type) {
            case VIDEO_CALL: return "Visioconférence";
            case PHONE_CALL: return "Téléphone";
            default:         return "Présentiel";
        }
    }

    private InterviewResponse toResponse(Interview i) {
        return InterviewResponse.builder()
                .id(i.getId())
                .applicationId(i.getApplication().getId())
                .jobOfferId(i.getJobOffer().getId())
                .jobTitle(i.getJobOffer().getTitle())
                .candidateUserId(i.getCandidateUserId())
                .candidateName(i.getCandidateName())
                .candidateEmail(i.getCandidateEmail())
                .interviewDate(i.getInterviewDate())
                .durationMinutes(i.getDurationMinutes())
                .location(i.getLocation())
                .type(i.getType() != null ? i.getType().toString() : "ON_SITE")
                .notes(i.getNotes())
                .status(i.getStatus() != null ? i.getStatus().toString() : "SCHEDULED")
                .emailSent(i.getEmailSent())
                .feedbackSubmitted(interviewCandidateFeedbackRepository.existsByInterviewId(i.getId()))
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .build();
    }
    public List<InterviewResponse> getInterviewsByUser(Long userId) {
        return interviewRepository.findByCandidateUserIdOrderByInterviewDateAsc(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}