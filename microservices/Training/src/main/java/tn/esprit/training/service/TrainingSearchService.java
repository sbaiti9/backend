package tn.esprit.training.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.training.entity.Training;
import tn.esprit.training.repository.TrainingSearchRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TrainingSearchService {
    private final TrainingSearchRepository searchRepo;

    public void index(Training training) {
        if (training == null || training.getId() == null) return;
        training.setEsId(String.valueOf(training.getId()));
        training.setName(training.getTitle());
        searchRepo.save(training);
    }

    public List<Training> search(String q) {
        if (q == null) q = "";
        q = q.trim();
        if (q.isEmpty()) return List.of();
        return searchRepo.findByNameContainingOrDescriptionContaining(q, q);
    }

    public void deleteFromIndex(Long id) {
        if (id == null) return;
        searchRepo.deleteById(String.valueOf(id));
    }
}

