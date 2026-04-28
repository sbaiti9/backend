package esprit.subscription.DTO;

import lombok.Data;

@Data
public class LivenessVerifyRequest {
    private String challengeToken;
    private double livenessScore;
    private double gestureScore;
    private double antiSpoofScore;

    /** True when client-side human-face geometry checks passed */
    private boolean faceValidated;
    /** Blinks detected during gesture holds */
    private int blinkCount;
    /** Session duration in ms (client clock) */
    private int timeUsedMs;
    /** Average eye aspect ratio over the session */
    private double earScore;
}
