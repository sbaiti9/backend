package tn.esprit.training.dto;

import java.math.BigDecimal;

public class LearningPathStepDTO {
    private Integer step;
    private Long courseId;
    private String title;
    private String reason;
    private Integer estimatedWeeks;
    private String category;
    private String level;
    private BigDecimal price;
    private String thumbnailUrl;

    public Integer getStep() {
        return step;
    }

    public void setStep(Integer step) {
        this.step = step;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getEstimatedWeeks() {
        return estimatedWeeks;
    }

    public void setEstimatedWeeks(Integer estimatedWeeks) {
        this.estimatedWeeks = estimatedWeeks;
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
}

