package esprit.subscription.entity;

public enum ChatSessionStatus {
    OPEN,
    /** Unlocked by human admin review. */
    GRANTED,
    REJECTED,
    /** Unlocked automatically by AI fraud agent. */
    AI_RESOLVED,
    /** Escalated: AI suggests human admin review. */
    NEEDS_HUMAN_REVIEW
}
