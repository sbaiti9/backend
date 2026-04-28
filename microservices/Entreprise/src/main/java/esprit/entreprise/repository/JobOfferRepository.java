package esprit.entreprise.repository;

import esprit.entreprise.entity.JobOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobOfferRepository extends JpaRepository<JobOffer, Long> {
    List<JobOffer> findByCompany_Id(Long companyId);
    @Query("SELECT j FROM JobOffer j WHERE (:contractType IS NULL OR j.contractType = :contractType) AND (:location IS NULL OR j.location = :location) AND (:remote IS NULL OR j.remote = :remote)")
    List<JobOffer> findByFilters(@Param("contractType") String contractType, @Param("location") String location, @Param("remote") String remote);
    long countByIsActive(Boolean isActive);

    @Query("SELECT j.contractType, COUNT(j) FROM JobOffer j GROUP BY j.contractType ORDER BY COUNT(j) DESC")
    List<Object[]> countByContractType();

    @Query("SELECT j.remote, COUNT(j) FROM JobOffer j GROUP BY j.remote ORDER BY COUNT(j) DESC")
    List<Object[]> countByRemote();

    @Query("SELECT j.location, COUNT(j) FROM JobOffer j GROUP BY j.location ORDER BY COUNT(j) DESC")
    List<Object[]> countByLocation();

    @Query("SELECT c.name, COUNT(j) FROM JobOffer j JOIN j.company c GROUP BY c.name ORDER BY COUNT(j) DESC")
    List<Object[]> countByCompany();

    @Query("SELECT j.company.id, COUNT(j), SUM(CASE WHEN j.isActive = true THEN 1 ELSE 0 END) FROM JobOffer j GROUP BY j.company.id")
    List<Object[]> countOffersAggregatedByCompanyId();

    @Query("SELECT AVG(j.salary) FROM JobOffer j WHERE j.salary IS NOT NULL")
    Double findAverageSalary();

    @Query("SELECT MIN(j.salary) FROM JobOffer j WHERE j.salary IS NOT NULL")
    Double findMinSalary();

    @Query("SELECT MAX(j.salary) FROM JobOffer j WHERE j.salary IS NOT NULL")
    Double findMaxSalary();
}
