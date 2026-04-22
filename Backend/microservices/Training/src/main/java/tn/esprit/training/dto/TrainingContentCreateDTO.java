package tn.esprit.training.dto;

import jakarta.validation.constraints.NotBlank;

public class TrainingContentCreateDTO {
    @NotBlank
    private String title;
    @NotBlank
    private String contentUrl;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContentUrl() {
        return contentUrl;
    }

    public void setContentUrl(String contentUrl) {
        this.contentUrl = contentUrl;
    }
}

