package tn.esprit.training.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "preview_progress")
@IdClass(PreviewProgressId.class)
public class PreviewProgress {
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "claimed_at", nullable = false)
    private LocalDateTime claimedAt = LocalDateTime.now();

    public PreviewProgress() {}

    public PreviewProgress(Long userId, Long contentId) {
        this.userId = userId;
        this.contentId = contentId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getContentId() {
        return contentId;
    }

    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setContentId(Long contentId) {
        this.contentId = contentId;
    }

    public void setClaimedAt(LocalDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }
}

