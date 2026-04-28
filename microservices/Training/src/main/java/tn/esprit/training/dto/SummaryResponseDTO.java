package tn.esprit.training.dto;

public class SummaryResponseDTO {
    private String summary;

    public SummaryResponseDTO() {}

    public SummaryResponseDTO(String summary) {
        this.summary = summary;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}

