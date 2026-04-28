package tn.esprit.training.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SentimentResponseDTO {
    /** "ai" = Claude summary, "rating" = estimate from star averages (no API or API failed). */
    private String source;
    private String mood;
    private int score;
    private String summary;

    public SentimentResponseDTO() {}

    public SentimentResponseDTO(String mood, int score, String summary) {
        this.mood = mood;
        this.score = score;
        this.summary = summary;
    }

    public String getMood() {
        return mood;
    }

    public void setMood(String mood) {
        this.mood = mood;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}

