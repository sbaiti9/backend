package esprit.entreprise.repository;

import esprit.entreprise.entity.Interview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InterviewRepository extends JpaRepository<Interview, Long> {

    // Tous les entretiens pour une offre d'emploi
    List<Interview> findByJobOfferIdOrderByInterviewDateAsc(Long jobOfferId);

    // Tous les entretiens pour une candidature
    List<Interview> findByApplicationId(Long applicationId);

    // Entretiens entre deux dates (pour le calendrier)
    List<Interview> findByJobOfferIdAndInterviewDateBetweenOrderByInterviewDateAsc(
            Long jobOfferId, LocalDateTime start, LocalDateTime end);

    // Entretiens à venir pour une offre
    List<Interview> findByJobOfferIdAndInterviewDateAfterOrderByInterviewDateAsc(
            Long jobOfferId, LocalDateTime date);

    // Vérifier si un entretien existe déjà pour cette candidature
    boolean existsByApplicationId(Long applicationId);
    List<Interview> findByCandidateUserIdOrderByInterviewDateAsc(Long userId);

}