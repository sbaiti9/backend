package esprit.entreprise.repository;

import esprit.entreprise.entity.ApplicationStatus;
import esprit.entreprise.entity.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    // Trouver les candidatures d'un utilisateur
    List<JobApplication> findByUserId(Long userId);

    // Trouver les candidatures pour une offre d'emploi (triées par score décroissant)
    @Query("SELECT ja FROM JobApplication ja WHERE ja.jobOffer.id = :jobOfferId ORDER BY ja.matchingScore DESC")
    List<JobApplication> findByJobOfferId(@Param("jobOfferId") Long jobOfferId);

    // Trouver une candidature spécifique
    Optional<JobApplication> findByUserIdAndJobOfferId(Long userId, Long jobOfferId);

    // Trouver les candidatures par statut
    @Query("SELECT ja FROM JobApplication ja WHERE ja.jobOffer.id = :jobOfferId AND ja.status = :status ORDER BY ja.matchingScore DESC")
    List<JobApplication> findByJobOfferIdAndStatus(
            @Param("jobOfferId") Long jobOfferId,
            @Param("status") ApplicationStatus status
    );

    // Trouver les candidatures triées par score de matching (DESC)
    @Query("SELECT ja FROM JobApplication ja WHERE ja.jobOffer.id = :jobOfferId AND ja.status IN :statuses ORDER BY ja.matchingScore DESC")
    List<JobApplication> findByJobOfferIdAndStatusesOrderByMatchingScoreDesc(
            @Param("jobOfferId") Long jobOfferId,
            @Param("statuses") List<ApplicationStatus> statuses
    );

    // Vérifier si un utilisateur a déjà postulé
    boolean existsByUserIdAndJobOfferId(Long userId, Long jobOfferId);

    // Compter les candidatures par statut
    long countByJobOfferIdAndStatus(Long jobOfferId, ApplicationStatus status);

    // Trouver les candidatures actives (PENDING, REVIEWED, SHORTLISTED)
    @Query("SELECT ja FROM JobApplication ja WHERE ja.jobOffer.id = :jobOfferId AND ja.status IN ('PENDING', 'REVIEWED', 'SHORTLISTED') ORDER BY ja.matchingScore DESC")
    List<JobApplication> findActiveApplicationsByJobOfferId(@Param("jobOfferId") Long jobOfferId);

    // Trouver les meilleures candidatures pour une offre (score >= 70)
    @Query("SELECT ja FROM JobApplication ja WHERE ja.jobOffer.id = :jobOfferId AND ja.matchingScore >= 70 ORDER BY ja.matchingScore DESC")
    List<JobApplication> findTopQualifiedApplications(@Param("jobOfferId") Long jobOfferId);

    // Compter toutes les candidatures pour une offre
    long countByJobOfferId(Long jobOfferId);

    @Query("SELECT j.company.id, COUNT(a) FROM JobApplication a JOIN a.jobOffer j GROUP BY j.company.id")
    List<Object[]> countApplicationsByCompanyId();
}