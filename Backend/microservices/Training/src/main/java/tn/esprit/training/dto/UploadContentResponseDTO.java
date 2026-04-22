package tn.esprit.training.dto;

public class UploadContentResponseDTO {
    private String url;

    public UploadContentResponseDTO() {}

    public UploadContentResponseDTO(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}

