package esprit.subscription.repository;

import esprit.subscription.entity.ChatSessionStatus;
import esprit.subscription.entity.UnlockChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface UnlockChatSessionRepository extends JpaRepository<UnlockChatSession, Long> {

    Optional<UnlockChatSession> findByUserIdAndStatus(String userId, ChatSessionStatus status);

    List<UnlockChatSession> findByStatusOrderByCreatedAtDesc(ChatSessionStatus status);

    List<UnlockChatSession> findByStatusInOrderByCreatedAtDesc(Collection<ChatSessionStatus> statuses);

    List<UnlockChatSession> findByUserIdOrderByCreatedAtDesc(String userId);

    @Query("SELECT DISTINCT s FROM UnlockChatSession s LEFT JOIN FETCH s.messages WHERE s.id = :id")
    Optional<UnlockChatSession> findByIdWithMessages(@Param("id") Long id);
}
