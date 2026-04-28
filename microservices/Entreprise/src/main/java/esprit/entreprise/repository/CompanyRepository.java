package esprit.entreprise.repository;

import esprit.entreprise.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    // ⭐ NEW: Find company by userId (to link User and Company)
    Optional<Company> findByUserId(Long userId);

    // Find by email
    Optional<Company> findByEmail(String email);

    // Analytics queries
    @Query("SELECT c.industry, COUNT(c) FROM Company c GROUP BY c.industry ORDER BY COUNT(c) DESC")
    List<Object[]> countByIndustry();

    @Query("SELECT c.location, COUNT(c) FROM Company c GROUP BY c.location ORDER BY COUNT(c) DESC")
    List<Object[]> countByLocation();
}