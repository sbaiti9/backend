package esprit.subscription.event;

import esprit.subscription.service.AdminNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Crée la ligne {@code admin_notifications} + SSE uniquement après commit de {@code user_blocks}.
 */
@Component
public class UserBlockedNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(UserBlockedNotificationListener.class);

    private final AdminNotificationService adminNotificationService;

    public UserBlockedNotificationListener(AdminNotificationService adminNotificationService) {
        this.adminNotificationService = adminNotificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserBlocked(UserBlockedEvent event) {
        try {
            adminNotificationService.notifyUserBlocked(event.userId(), event.reason(), event.userBlockId());
        } catch (Exception e) {
            log.warn(
                    "notifyUserBlocked failed after commit for userId={} blockId={}: {}",
                    event.userId(),
                    event.userBlockId(),
                    e.getMessage(),
                    e);
        }
    }
}
