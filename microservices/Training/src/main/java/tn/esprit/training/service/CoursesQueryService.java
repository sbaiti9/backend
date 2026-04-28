package tn.esprit.training.service;

import org.springframework.stereotype.Service;
import tn.esprit.training.dto.TrainingResponseDTO;
import tn.esprit.training.entity.Training;
import tn.esprit.training.repository.AvisRepository;
import tn.esprit.training.repository.TrainingContentRepository;
import tn.esprit.training.repository.TrainingRepository;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CoursesQueryService {
    private final TrainingRepository trainingRepository;
    private final AvisRepository avisRepository;
    private final TrainingContentRepository contentRepository;

    public CoursesQueryService(
            TrainingRepository trainingRepository,
            AvisRepository avisRepository,
            TrainingContentRepository contentRepository
    ) {
        this.trainingRepository = trainingRepository;
        this.avisRepository = avisRepository;
        this.contentRepository = contentRepository;
    }

    public Map<String, Object> query(
            String q,
            List<String> categories,
            List<String> levels,
            String priceLevel,
            String sort,
            int page,
            int size
    ) {
        List<Training> all = trainingRepository.findAll();

        List<Training> filtered = all.stream().filter(t -> {
            boolean ok = true;
            if (q != null && !q.isBlank()) {
                String qq = q.toLowerCase();
                ok &= (opt(t.getTitle()).contains(qq)
                        || opt(t.getDescription()).contains(qq)
                        || opt(t.getCategory()).contains(qq));
            }
            if (categories != null && !categories.isEmpty()) {
                ok &= categories.stream().map(String::toLowerCase).anyMatch(c -> opt(t.getCategory()).equals(c));
            }
            if (levels != null && !levels.isEmpty()) {
                ok &= levels.stream().map(String::toLowerCase).anyMatch(l -> opt(t.getLevel()).equals(l));
            }
            if (priceLevel != null && !"ALL".equalsIgnoreCase(priceLevel)) {
                boolean free = (t.getPrice() != null && t.getPrice().doubleValue() == 0.0);
                if ("FREE".equalsIgnoreCase(priceLevel)) ok &= free;
                if ("PAID".equalsIgnoreCase(priceLevel)) ok &= !free;
            }
            return ok;
        }).collect(Collectors.toList());

        Comparator<Training> comparator = Comparator.comparing(Training::getId).reversed();
        if ("OLDEST".equalsIgnoreCase(sort)) comparator = Comparator.comparing(Training::getId);
        if ("PRICE_ASC".equalsIgnoreCase(sort)) comparator = Comparator.comparing(t -> t.getPrice());
        if ("PRICE_DESC".equalsIgnoreCase(sort)) comparator = Comparator.comparing((Training t) -> t.getPrice()).reversed();
        if ("TOP_RATED".equalsIgnoreCase(sort)) comparator = Comparator.comparingDouble((Training t) -> avg(t.getId())).reversed();
        if ("MOST_REVIEWED".equalsIgnoreCase(sort)) comparator = Comparator.comparingLong((Training t) -> count(t.getId())).reversed();
        filtered.sort(comparator);

        int totalElements = filtered.size();
        int totalPages = (int) Math.ceil((double) totalElements / Math.max(size, 1));
        int from = Math.max(0, page * size);
        int to = Math.min(totalElements, from + size);
        List<Training> pageItems = from < to ? filtered.subList(from, to) : Collections.emptyList();

        List<TrainingResponseDTO> content = pageItems.stream().map(t -> {
            TrainingResponseDTO dto = tn.esprit.training.mapper.TrainingMapper.toDto(t);
            long reviews = count(t.getId());
            Double avgRating = avg(t.getId());
            dto.setReviewsCount((int) reviews);
            dto.setAverageRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : null);
            return dto;
        }).collect(Collectors.toList());

        Map<String, Object> resp = new HashMap<>();
        resp.put("content", content);
        resp.put("page", page);
        resp.put("size", size);
        resp.put("totalElements", totalElements);
        resp.put("totalPages", totalPages);
        return resp;
    }

    private String opt(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    private Double avg(Long id) {
        return avisRepository.averageRatingByTrainingId(id);
    }

    private long count(Long id) {
        return avisRepository.countByTraining_Id(id);
    }
}

