package tn.esprit.training.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.training.dto.AvisCreateDTO;
import tn.esprit.training.dto.AvisDTO;
import tn.esprit.training.entity.Avis;
import tn.esprit.training.entity.Training;
import tn.esprit.training.mapper.TrainingMapper;
import tn.esprit.training.repository.AvisRepository;
import tn.esprit.training.repository.TrainingRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class ReviewService {
    private final TrainingRepository trainingRepository;
    private final AvisRepository avisRepository;

    public ReviewService(TrainingRepository trainingRepository, AvisRepository avisRepository) {
        this.trainingRepository = trainingRepository;
        this.avisRepository = avisRepository;
    }

    public List<AvisDTO> getReviewsByTraining(Long trainingId) {
        return avisRepository.findByTraining_IdOrderByCreatedAtDesc(trainingId)
                .stream()
                .map(TrainingMapper::toDto)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getReviewsPaged(Long trainingId, int page, int pageSize, String sort) {
        Sort s;
        if ("oldest".equalsIgnoreCase(sort)) {
            s = Sort.by(Sort.Direction.ASC, "createdAt");
        } else if ("highest".equalsIgnoreCase(sort)) {
            s = Sort.by(Sort.Direction.DESC, "rating");
        } else if ("lowest".equalsIgnoreCase(sort)) {
            s = Sort.by(Sort.Direction.ASC, "rating");
        } else {
            s = Sort.by(Sort.Direction.DESC, "createdAt");
        }
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, pageSize), s);
        Page<Avis> p = avisRepository.findByTraining_Id(trainingId, pageable);
        List<AvisDTO> items = p.getContent().stream().map(TrainingMapper::toDto).collect(Collectors.toList());
        Map<String, Object> resp = new HashMap<>();
        resp.put("items", items);
        resp.put("totalCount", p.getTotalElements());
        resp.put("currentPage", page);
        resp.put("pageSize", pageSize);
        return resp;
    }

    public AvisDTO addReview(Long trainingId, @Valid AvisCreateDTO req) {
        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new EntityNotFoundException("Training not found"));
        Avis avis = new Avis();
        avis.setId(avisRepository.nextId());
        avis.setTraining(training);
        avis.setAuthorName(req.getAuthorName());
        avis.setRating(req.getRating());
        avis.setComment(req.getComment());
        Avis saved = avisRepository.save(avis);
        return TrainingMapper.toDto(saved);
    }

    public void deleteReview(Long id) {
        avisRepository.deleteById(id);
    }
}

