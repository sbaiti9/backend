package esprit.subscription.entity;

import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;

public enum ChallengeGesture {
    TWO_FINGERS,
    THUMBS_UP,
    OPEN_PALM,
    ONE_FINGER,
    FIST;

    private static final ChallengeGesture[] VALUES = values();

    public static ChallengeGesture random() {
        return VALUES[ThreadLocalRandom.current().nextInt(VALUES.length)];
    }

    public String frenchLabel() {
        return switch (this) {
            case TWO_FINGERS -> "Montrez deux doigts";
            case THUMBS_UP -> "Pouce levé";
            case OPEN_PALM -> "Paume ouverte (cinq doigts)";
            case ONE_FINGER -> "Un seul doigt levé";
            case FIST -> "Poing fermé";
        };
    }

    public static EnumSet<ChallengeGesture> all() {
        return EnumSet.allOf(ChallengeGesture.class);
    }
}
