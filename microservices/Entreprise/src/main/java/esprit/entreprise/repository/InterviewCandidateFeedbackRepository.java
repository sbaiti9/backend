package esprit.entreprise.repository;

import esprit.entreprise.entity.InterviewCandidateFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewCandidateFeedbackRepository extends JpaRepository<InterviewCandidateFeedback, Long> {

    boolean existsByInterviewId(Long interviewId);

    Optional<InterviewCandidateFeedback> findByInterviewId(Long interviewId);

    void deleteByJobOfferId(Long jobOfferId);

    @Query("SELECT COUNT(f), AVG(f.overallRating), AVG(f.professionalismRating), AVG(f.punctualityRating), AVG(f.clarityRating) "
            + "FROM InterviewCandidateFeedback f WHERE f.companyId = :companyId")
    List<Object[]> aggregateForCompany(@Param("companyId") Long companyId);

    @Query("SELECT COUNT(f) FROM InterviewCandidateFeedback f")
    long countTotal();

    @Query("SELECT AVG(f.overallRating) FROM InterviewCandidateFeedback f")
    Double avgOverallGlobal();

    @Query("SELECT f.companyId, c.name, COUNT(f), AVG(f.overallRating), AVG(f.professionalismRating), AVG(f.punctualityRating), AVG(f.clarityRating) "
            + "FROM InterviewCandidateFeedback f JOIN Company c ON c.id = f.companyId "
            + "GROUP BY f.companyId, c.name ORDER BY COUNT(f) DESC")
    List<Object[]> aggregateByCompanyForAdmin();
}
