package tn.esprit.training.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PriceOptimizationItemDTO {
    private long courseId;
    private double currentPrice;
    private double suggestedPrice;
    private String reason;
    private String status;

    public PriceOptimizationItemDTO() {
    }

    public PriceOptimizationItemDTO(long courseId, double currentPrice, double suggestedPrice, String reason, String status) {
        this.courseId = courseId;
        this.currentPrice = currentPrice;
        this.suggestedPrice = suggestedPrice;
        this.reason = reason;
        this.status = status;
    }

    @JsonProperty("courseId")
    public long getCourseId() {
        return courseId;
    }

    public void setCourseId(long courseId) {
        this.courseId = courseId;
    }

    @JsonProperty("currentPrice")
    public double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    @JsonProperty("suggestedPrice")
    public double getSuggestedPrice() {
        return suggestedPrice;
    }

    public void setSuggestedPrice(double suggestedPrice) {
        this.suggestedPrice = suggestedPrice;
    }

    @JsonProperty("reason")
    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @JsonProperty("status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

