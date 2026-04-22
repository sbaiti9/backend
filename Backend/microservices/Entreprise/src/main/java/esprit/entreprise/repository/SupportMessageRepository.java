package esprit.entreprise.repository;

import esprit.entreprise.entity.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(exported = false)
public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    List<SupportMessage> findByCompanyIdOrderByCreatedAtAsc(Long companyId);

    @Query("SELECT COUNT(m) FROM SupportMessage m WHERE m.senderRole = 'ENTREPRISE' "
            + "AND (m.readByAdmin IS NULL OR m.readByAdmin = false)")
    long countUnreadFromEnterpriseForAdmin();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SupportMessage m SET m.readByAdmin = true WHERE m.companyId = :companyId "
            + "AND m.senderRole = 'ENTREPRISE' AND (m.readByAdmin IS NULL OR m.readByAdmin = false)")
    int markReadByAdminForCompany(@Param("companyId") Long companyId);

    @Query("SELECT m.companyId, COUNT(m) FROM SupportMessage m GROUP BY m.companyId")
    List<Object[]> countMessagesByCompanyId();

    @Query("SELECT m.companyId, COUNT(m) FROM SupportMessage m WHERE m.senderRole = 'ENTREPRISE' GROUP BY m.companyId")
    List<Object[]> countEnterpriseMessagesByCompanyId();
}
