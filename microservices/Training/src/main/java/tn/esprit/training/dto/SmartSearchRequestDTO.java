package tn.esprit.training.dto;

import jakarta.validation.constraints.NotBlank;

public class SmartSearchRequestDTO {
    @NotBlank
    private String query;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}

