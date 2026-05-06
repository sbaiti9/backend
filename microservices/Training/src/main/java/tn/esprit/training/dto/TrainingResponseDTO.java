package tn.esprit.training.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public class TrainingResponseDTO implements Serializable {
    private Long id;
    private String title;
    private String description;
    private String category;
    private String level;
    private BigDecimal price;
    private String thumbnailUrl;
    private String language;
    private String status;
    private List<TrainingContentDTO> contents;
    private Integer reviewsCount;
    private Double averageRating;
    private String matchReason;
    private Integer relevanceScore;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<TrainingContentDTO> getContents() {
        return contents;
    }

    public void setContents(List<TrainingContentDTO> contents) {
        this.contents = contents;
    }

    public Integer getReviewsCount() {
        return reviewsCount;
    }

    public void setReviewsCount(Integer reviewsCount) {
        this.reviewsCount = reviewsCount;
    }

    public Double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(Double averageRating) {
        this.averageRating = averageRating;
    }

    public String getMatchReason() {
        return matchReason;
    }

    public void setMatchReason(String matchReason) {
        this.matchReason = matchReason;
    }

    public Integer getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(Integer relevanceScore) {
        this.relevanceScore = relevanceScore;
    }
}

