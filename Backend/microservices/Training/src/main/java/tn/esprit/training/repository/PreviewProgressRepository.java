package tn.esprit.training.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.training.entity.PreviewProgress;
import tn.esprit.training.entity.PreviewProgressId;

import java.util.Collection;
import java.util.List;

public interface PreviewProgressRepository extends JpaRepository<PreviewProgress, PreviewProgressId> {
    long countByUserIdAndContentIdIn(Long userId, Collection<Long> contentIds);
    List<PreviewProgress> findByUserIdAndContentIdIn(Long userId, Collection<Long> contentIds);
    boolean existsByUserIdAndContentId(Long userId, Long contentId);
}

