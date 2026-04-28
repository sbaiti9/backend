package tn.esprit.training.dto;

/** action: ALLOW | WARN | EJECT */
public class ProctorAnalyzeResponseDTO {
    private boolean cheating;
    private String confidence;
    private String reason;
    private String action;
    /** Optional: DARK | BLURRY | TOO_SMALL | OK | SKIPPED */
    private String frameQuality;
    /** FACE_CHECK only: true only when a clear face was verified (null during MONITOR). */
    private Boolean faceVisible;

    public ProctorAnalyzeResponseDTO() {}

    public ProctorAnalyzeResponseDTO(boolean cheating, String confidence, String reason, String action, String frameQuality) {
        this.cheating = cheating;
        this.confidence = confidence;
        this.reason = reason;
        this.action = action;
        this.frameQuality = frameQuality;
    }

    public boolean isCheating() {
        return cheating;
    }

    public void setCheating(boolean cheating) {
        this.cheating = cheating;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getFrameQuality() {
        return frameQuality;
    }

    public void setFrameQuality(String frameQuality) {
        this.frameQuality = frameQuality;
    }

    public Boolean getFaceVisible() {
        return faceVisible;
    }

    public void setFaceVisible(Boolean faceVisible) {
        this.faceVisible = faceVisible;
    }
}
