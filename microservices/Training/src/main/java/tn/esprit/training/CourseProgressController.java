package tn.esprit.training;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.training.dto.CourseProgressDTO;
import tn.esprit.training.entity.PreviewProgress;
import tn.esprit.training.repository.PreviewProgressRepository;
import tn.esprit.training.repository.TrainingContentRepository;
import tn.esprit.training.repository.TrainingRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/courses", "/api/trainings"})
public class CourseProgressController {

    private static final Logger logger = LoggerFactory.getLogger(CourseProgressController.class);

    private final TrainingRepository trainingRepository;
    private final TrainingContentRepository contentRepository;
    private final PreviewProgressRepository progressRepository;

    public CourseProgressController(
            TrainingRepository trainingRepository,
            TrainingContentRepository contentRepository,
            PreviewProgressRepository progressRepository
    ) {
        this.trainingRepository = trainingRepository;
        this.contentRepository = contentRepository;
        this.progressRepository = progressRepository;
    }

    @PostMapping("/{courseId}/contents/{contentId}/claim")
    public ResponseEntity<?> claim(
            @PathVariable Long courseId,
            @PathVariable Long contentId,
            HttpServletRequest request
    ) {
        Long userId = resolveUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing user identity", "details", "Provide X-User-Id header (or ?userId=)"));
        }

        var contentOpt = contentRepository.findById(contentId);
        if (contentOpt.isEmpty()
                || contentOpt.get().getTraining() == null
                || !courseId.equals(contentOpt.get().getTraining().getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Content does not belong to course", "details", "courseId/contentId mismatch"));
        }

        boolean exists = progressRepository.existsByUserIdAndContentId(userId, contentId);
        if (!exists) {
            try {
                progressRepository.save(new PreviewProgress(userId, contentId));
                progressRepository.flush();
            } catch (DataIntegrityViolationException ignored) {
            }
        }

        CourseProgressDTO dto = progressFor(userId, courseId);
        logger.info("Claim OK userId={}, courseId={}, completed={}/{} percent={} points={}",
                userId, courseId, dto.getCompletedPreviews(), dto.getTotalPreviews(), dto.getProgressPercent(), dto.getPoints());

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{courseId}/progress")
    public ResponseEntity<?> progress(@PathVariable Long courseId, HttpServletRequest request) {
        Long userId = resolveUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing user identity", "details", "Provide X-User-Id header (or ?userId=)"));
        }

        CourseProgressDTO dto = progressFor(userId, courseId);
        return ResponseEntity.ok(dto);
    }

    private CourseProgressDTO progressFor(Long userId, Long courseId) {
        var contents = contentRepository.findByTraining_Id(courseId);
        List<Long> contentIds = contents.stream().map(c -> c.getId()).collect(Collectors.toList());

        int total = contentIds.size();

        var claimed = contentIds.isEmpty()
                ? List.<PreviewProgress>of()
                : progressRepository.findByUserIdAndContentIdIn(userId, contentIds);

        List<Long> doneIds = claimed.stream().map(PreviewProgress::getContentId).collect(Collectors.toList());

        int completed = doneIds.size();
        int percent = total == 0 ? 0 : (int) Math.round((completed * 100.0) / total);
        int points = completed * 10;

        CourseProgressDTO dto = new CourseProgressDTO();
        dto.setTotalPreviews(total);
        dto.setCompletedPreviews(completed);
        dto.setProgressPercent(percent);
        dto.setPoints(points);
        dto.setCertificateUnlocked(total > 0 && completed == total);
        dto.setCompletedContentIds(doneIds);

        logger.info("Computed progress courseId={}, userId={}, total={}, completed={}, percent={}",
                courseId, userId, total, completed, percent);

        return dto;
    }

    private Long resolveUserId(HttpServletRequest request) {
        String header = request.getHeader("X-User-Id");
        if (header != null && !header.isBlank()) {
            try {
                return Long.parseLong(header.trim());
            } catch (NumberFormatException ignored) {
            }
        }

        String param = request.getParameter("userId");
        if (param != null && !param.isBlank()) {
            try {
                return Long.parseLong(param.trim());
            } catch (NumberFormatException ignored) {
            }
        }

        return null;
    }
}

