package tn.esprit.user.entity;

/**
 * Palier d’engagement basé sur les points plateforme cumulés ({@code platform_engagement_points}).
 */
public enum PlatformEngagementTier {
    BRONZE,
    SILVER,
    GOLD;

    /** Seuil minimum pour Argent. */
    public static final int SILVER_MIN_POINTS = 100;
    /** Seuil minimum pour Or. */
    public static final int GOLD_MIN_POINTS = 300;

    public static PlatformEngagementTier fromTotalPoints(int totalPoints) {
        if (totalPoints >= GOLD_MIN_POINTS) {
            return GOLD;
        }
        if (totalPoints >= SILVER_MIN_POINTS) {
            return SILVER;
        }
        return BRONZE;
    }

    public String getLabelFr() {
        return switch (this) {
            case BRONZE -> "Bronze";
            case SILVER -> "Argent";
            case GOLD -> "Or";
        };
    }
}
