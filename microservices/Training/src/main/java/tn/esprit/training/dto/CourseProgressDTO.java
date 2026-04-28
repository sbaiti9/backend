package tn.esprit.training.dto;

import java.util.List;

public class CourseProgressDTO {
    private int totalPreviews;
    private int completedPreviews;
    private int progressPercent;
    private int points;
    private boolean certificateUnlocked;
    private List<Long> completedContentIds;

    public int getTotalPreviews() {
        return totalPreviews;
    }

    public int getCompletedPreviews() {
        return completedPreviews;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public int getPoints() {
        return points;
    }

    public boolean isCertificateUnlocked() {
        return certificateUnlocked;
    }

    public List<Long> getCompletedContentIds() {
        return completedContentIds;
    }

    public void setTotalPreviews(int totalPreviews) {
        this.totalPreviews = totalPreviews;
    }

    public void setCompletedPreviews(int completedPreviews) {
        this.completedPreviews = completedPreviews;
    }

    public void setProgressPercent(int progressPercent) {
        this.progressPercent = progressPercent;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public void setCertificateUnlocked(boolean certificateUnlocked) {
        this.certificateUnlocked = certificateUnlocked;
    }

    public void setCompletedContentIds(List<Long> completedContentIds) {
        this.completedContentIds = completedContentIds;
    }
}

