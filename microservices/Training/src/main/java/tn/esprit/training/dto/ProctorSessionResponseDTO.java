package tn.esprit.training.dto;

public class ProctorSessionResponseDTO {
    private String sessionToken;
    private long expiresAtEpochMs;

    public ProctorSessionResponseDTO() {}

    public ProctorSessionResponseDTO(String sessionToken, long expiresAtEpochMs) {
        this.sessionToken = sessionToken;
        this.expiresAtEpochMs = expiresAtEpochMs;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public long getExpiresAtEpochMs() {
        return expiresAtEpochMs;
    }

    public void setExpiresAtEpochMs(long expiresAtEpochMs) {
        this.expiresAtEpochMs = expiresAtEpochMs;
    }
}
