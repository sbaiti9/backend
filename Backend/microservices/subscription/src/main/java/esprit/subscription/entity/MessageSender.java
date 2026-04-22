package esprit.subscription.entity;

public enum MessageSender {
    USER,
    ADMIN,
    /** Automated system notices (e.g. AI resolution). */
    SYSTEM
}
