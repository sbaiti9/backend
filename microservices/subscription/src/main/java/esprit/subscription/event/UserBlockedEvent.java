package esprit.subscription.event;

/**
 * Publié après enregistrement d’un {@link esprit.subscription.entity.UserBlock} — traité
 * {@link org.springframework.transaction.event.TransactionPhase#AFTER_COMMIT} pour insérer la notification admin.
 */
public record UserBlockedEvent(String userId, String reason, Long userBlockId) {}
