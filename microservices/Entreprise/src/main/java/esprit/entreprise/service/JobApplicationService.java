package esprit.entreprise.service;

import esprit.entreprise.entity.JobApplication;
import esprit.entreprise.entity.ApplicationStatus;
import esprit.entreprise.entity.JobOffer;
import esprit.entreprise.repository.JobApplicationRepository;
import esprit.entreprise.repository.JobOfferRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Service
@Slf4j
public class JobApplicationService {

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private JobOfferRepository jobOfferRepository;

    /**
     * Créer une nouvelle candidature avec calcul du score de matching
     *
     * @param userId - ID de l'utilisateur (depuis User MS)
     * @param jobOfferId - ID de l'offre d'emploi
     * @param educationLevel - Niveau d'étude: "Bac", "Bac_plus_2", "Bac_plus_3", "Autre"
     * @param userStatus - Statut utilisateur: "ACTIVE", "EN_ATTENTE", "NON_ACTIVE"
     * @param averageRating - Notation moyenne: 0.0 à 5.0
     * @param candidateName - Nom du candidat
     * @param candidateEmail - Email du candidat
     * @param coverLetter - Message de candidature (optionnel)
     */
    @Transactional
    public JobApplication createApplication(
            Long userId,
            Long jobOfferId,
            String educationLevel,
            String userStatus,
            Double averageRating,
            String candidateName,
            String candidateEmail,
            String coverLetter
    ) throws Exception {

        // Vérifier si l'utilisateur a déjà postulé
        if (jobApplicationRepository.existsByUserIdAndJobOfferId(userId, jobOfferId)) {
            throw new Exception("❌ Vous avez déjà postulé à cette offre!");
        }

        // Vérifier que l'offre existe
        JobOffer jobOffer = jobOfferRepository.findById(jobOfferId)
                .orElseThrow(() -> new Exception("❌ Offre d'emploi non trouvée"));

        // Créer la candidature
        JobApplication application = JobApplication.builder()
                .userId(userId)
                .jobOffer(jobOffer)
                .coverLetter(coverLetter)
                .status(ApplicationStatus.PENDING)
                .candidateName(candidateName)
                .candidateEmail(candidateEmail)
                .educationLevel(educationLevel != null ? educationLevel : "Autre")
                .candidateStatus(userStatus != null ? userStatus : "EN_ATTENTE")
                .candidateAverageRating(averageRating != null ? averageRating : 0.0)
                .build();

        // Calculer les scores
        application.setEducationLevelScore(calculateEducationScore(educationLevel));
        application.setStatusScore(calculateStatusScore(userStatus));
        application.setRatingScore(calculateRatingScore(averageRating));

        // Calculer le score total
        application.calculateMatchingScore();

        JobApplication saved = jobApplicationRepository.save(application);

        log.info("✅ Candidature créée - User: {}, Offre: '{}', Score: {}%",
                userId, jobOffer.getTitle(), saved.getMatchingScore());

        return saved;
    }

    @Transactional
    public JobApplication attachCv(Long applicationId, String fileName, String contentType, byte[] data) throws Exception {
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new Exception("❌ Candidature non trouvée"));

        application.setCvFileName(fileName);
        application.setCvContentType(contentType);
        application.setCvData(data);

        return jobApplicationRepository.save(application);
    }

    /**
     * Calcule le score basé sur le niveau d'étude (0-100)
     * Bac_plus_3 = 100, Bac_plus_2 = 75, Bac = 50, Autre = 25
     */
    private Double calculateEducationScore(String educationLevel) {
        if (educationLevel == null) return 25.0;

        return switch (educationLevel) {
            case "Bac_plus_3" -> 100.0;
            case "Bac_plus_2" -> 75.0;
            case "Bac" -> 50.0;
            case "Autre" -> 25.0;
            default -> 25.0;
        };
    }

    /**
     * Calcule le score basé sur le statut (0-100)
     * ACTIVE = 100, EN_ATTENTE = 60, NON_ACTIVE = 0
     */
    private Double calculateStatusScore(String status) {
        if (status == null) return 0.0;

        return switch (status) {
            case "ACTIVE" -> 100.0;
            case "EN_ATTENTE" -> 60.0;
            case "NON_ACTIVE" -> 0.0;
            default -> 0.0;
        };
    }

    /**
     * Calcule le score basé sur la notation moyenne (0-100)
     * Convertit 0.0-5.0 en 0-100
     */
    private Double calculateRatingScore(Double rating) {
        if (rating == null || rating < 0) return 0.0;
        if (rating > 5.0) rating = 5.0;

        // Conversion: 5.0 = 100, 0.0 = 0
        return (rating / 5.0) * 100.0;
    }

    /**
     * Obtenir les candidatures d'un utilisateur
     */
    public List<JobApplication> getUserApplications(Long userId) {
        return jobApplicationRepository.findByUserId(userId);
    }

    /**
     * Obtenir les candidatures pour une offre (triées par score - meilleur au pire)
     */
    public List<JobApplication> getJobApplications(Long jobOfferId) {
        return jobApplicationRepository.findByJobOfferId(jobOfferId);
    }

    /**
     * Obtenir les candidatures actives triées par score
     */
    public List<JobApplication> getActiveApplications(Long jobOfferId) {
        return jobApplicationRepository.findActiveApplicationsByJobOfferId(jobOfferId);
    }

    /**
     * Obtenir les meilleures candidatures (score >= 70)
     */
    public List<JobApplication> getTopQualifiedApplications(Long jobOfferId) {
        return jobApplicationRepository.findTopQualifiedApplications(jobOfferId);
    }

    /**
     * Obtenir une candidature par ID
     */
    public Optional<JobApplication> getApplication(Long applicationId) {
        return jobApplicationRepository.findById(applicationId);
    }

    /**
     * Mettre à jour le statut d'une candidature
     */
    @Transactional
    public JobApplication updateApplicationStatus(Long applicationId, ApplicationStatus newStatus) throws Exception {
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new Exception("❌ Candidature non trouvée"));

        application.setStatus(newStatus);
        JobApplication updated = jobApplicationRepository.save(application);

        log.info("📝 Candidature {} - Statut: {} -> {}", applicationId, application.getStatus(), newStatus);

        return updated;
    }

    /**
     * Retirer une candidature
     */
    @Transactional
    public void withdrawApplication(Long applicationId) throws Exception {
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new Exception("❌ Candidature non trouvée"));

        application.setStatus(ApplicationStatus.WITHDRAWN);
        jobApplicationRepository.save(application);

        log.info("❌ Candidature {} retirée par le candidat", applicationId);
    }

    /**
     * Accepter une candidature
     */
    @Transactional
    public JobApplication acceptApplication(Long applicationId) throws Exception {
        return updateApplicationStatus(applicationId, ApplicationStatus.ACCEPTED);
    }

    /**
     * Rejeter une candidature
     */
    @Transactional
    public JobApplication rejectApplication(Long applicationId) throws Exception {
        return updateApplicationStatus(applicationId, ApplicationStatus.REJECTED);
    }

    /**
     * Passer à l'étape "Shortlisted"
     */
    @Transactional
    public JobApplication shortlistApplication(Long applicationId) throws Exception {
        return updateApplicationStatus(applicationId, ApplicationStatus.SHORTLISTED);
    }

    /**
     * Compter les candidatures acceptées pour une offre
     */
    public long countAcceptedApplications(Long jobOfferId) {
        return jobApplicationRepository.countByJobOfferIdAndStatus(jobOfferId, ApplicationStatus.ACCEPTED);
    }

    /**
     * Compter les candidatures pour une offre
     */
    public long countApplications(Long jobOfferId) {
        return jobApplicationRepository.countByJobOfferId(jobOfferId);
    }

    /**
     * Vérifie si un utilisateur a déjà postulé
     */
    public boolean hasAlreadyApplied(Long userId, Long jobOfferId) {
        return jobApplicationRepository.existsByUserIdAndJobOfferId(userId, jobOfferId);
    }

    /**
     * Supprimer une candidature (admin)
     */
    @Transactional
    public void deleteApplication(Long applicationId) throws Exception {
        if (!jobApplicationRepository.existsById(applicationId)) {
            throw new Exception("❌ Candidature non trouvée");
        }
        jobApplicationRepository.deleteById(applicationId);
        log.info("🗑️ Candidature {} supprimée", applicationId);
    }

    /**
     * Obtenir les statistiques de candidatures pour une offre
     */
    public Map<String, Long> getApplicationStats(Long jobOfferId) {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", jobApplicationRepository.countByJobOfferId(jobOfferId));
        stats.put("pending", jobApplicationRepository.countByJobOfferIdAndStatus(jobOfferId, ApplicationStatus.PENDING));
        stats.put("reviewed", jobApplicationRepository.countByJobOfferIdAndStatus(jobOfferId, ApplicationStatus.REVIEWED));
        stats.put("shortlisted", jobApplicationRepository.countByJobOfferIdAndStatus(jobOfferId, ApplicationStatus.SHORTLISTED));
        stats.put("accepted", jobApplicationRepository.countByJobOfferIdAndStatus(jobOfferId, ApplicationStatus.ACCEPTED));
        stats.put("rejected", jobApplicationRepository.countByJobOfferIdAndStatus(jobOfferId, ApplicationStatus.REJECTED));
        stats.put("withdrawn", jobApplicationRepository.countByJobOfferIdAndStatus(jobOfferId, ApplicationStatus.WITHDRAWN));
        return stats;
    }
}