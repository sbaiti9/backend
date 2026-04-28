package esprit.subscription.repository;

import esprit.subscription.entity.MessageSender;
import esprit.subscription.entity.UnlockChatMessage;
import esprit.subscription.entity.UnlockChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface UnlockChatMessageRepository extends JpaRepository<UnlockChatMessage, Long> {

    List<UnlockChatMessage> findBySessionOrderBySentAtAsc(UnlockChatSession session);

    Optional<UnlockChatMessage> findFirstBySessionOrderBySentAtDesc(UnlockChatSession session);

    long countBySession(UnlockChatSession session);

    long countBySessionAndSender(UnlockChatSession session, MessageSender sender);
}
