package tn.esprit.training.dto;

/**
 * phase: FACE_CHECK (before quiz) or MONITOR (during quiz).
 */
public class ProctorAnalyzeRequestDTO {
    private String sessionToken;
    private String base64Image;
    private String phase;

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getBase64Image() {
        return base64Image;
    }

    public void setBase64Image(String base64Image) {
        this.base64Image = base64Image;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }
}
