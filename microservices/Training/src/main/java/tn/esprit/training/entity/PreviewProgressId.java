package tn.esprit.training.entity;

import java.io.Serializable;
import java.util.Objects;

public class PreviewProgressId implements Serializable {
    private Long userId;
    private Long contentId;

    public PreviewProgressId() {}

    public PreviewProgressId(Long userId, Long contentId) {
        this.userId = userId;
        this.contentId = contentId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getContentId() {
        return contentId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setContentId(Long contentId) {
        this.contentId = contentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PreviewProgressId)) return false;
        PreviewProgressId that = (PreviewProgressId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(contentId, that.contentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, contentId);
    }
}

