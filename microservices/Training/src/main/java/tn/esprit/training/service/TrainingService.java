package tn.esprit.training.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.training.dto.AvisCreateDTO;
import tn.esprit.training.dto.AvisDTO;
import tn.esprit.training.dto.TrainingContentCreateDTO;
import tn.esprit.training.dto.TrainingContentDTO;
import tn.esprit.training.dto.TrainingRequestDTO;
import tn.esprit.training.dto.TrainingResponseDTO;
import tn.esprit.training.entity.Avis;
import tn.esprit.training.entity.Training;
import tn.esprit.training.entity.TrainingContent;
import tn.esprit.training.mapper.TrainingMapper;
import tn.esprit.training.repository.AvisRepository;
import tn.esprit.training.repository.TrainingContentRepository;
import tn.esprit.training.repository.TrainingRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class TrainingService {
    private final TrainingRepository trainingRepository;
    private final TrainingContentRepository contentRepository;
    private final AvisRepository avisRepository;
    private final PushNotificationService pushNotificationService;
    private final TrainingSearchService trainingSearchService;

    public TrainingService(
            TrainingRepository trainingRepository,
            TrainingContentRepository contentRepository,
            AvisRepository avisRepository,
            PushNotificationService pushNotificationService,
            TrainingSearchService trainingSearchService
    ) {
        this.trainingRepository = trainingRepository;
        this.contentRepository = contentRepository;
        this.avisRepository = avisRepository;
        this.pushNotificationService = pushNotificationService;
        this.trainingSearchService = trainingSearchService;
    }

    @Cacheable(value = "trainings-all", key = "'all'")
    public List<TrainingResponseDTO> getAll() {
        return trainingRepository.findAll()
                .stream()
                .map(t -> {
                    TrainingResponseDTO dto = TrainingMapper.toDto(t);
                    long count = avisRepository.countByTraining_Id(t.getId());
                    Double avg = avisRepository.averageRatingByTrainingId(t.getId());
                    dto.setReviewsCount((int) count);
                    dto.setAverageRating(avg != null ? Math.round(avg * 10.0) / 10.0 : null);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Cacheable(value = "trainings", key = "#id")
    public TrainingResponseDTO getById(Long id) {
        Training training = trainingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Training not found"));
        TrainingResponseDTO dto = TrainingMapper.toDto(training);
        long count = avisRepository.countByTraining_Id(training.getId());
        Double avg = avisRepository.averageRatingByTrainingId(training.getId());
        dto.setReviewsCount((int) count);
        dto.setAverageRating(avg != null ? Math.round(avg * 10.0) / 10.0 : null);
        return dto;
    }

    @CacheEvict(value = {"trainings", "trainings-all"}, allEntries = true)
    public TrainingResponseDTO create(@Valid TrainingRequestDTO req) {
        Training entity = TrainingMapper.toEntity(req);
        Training saved = trainingRepository.save(entity);
        trainingSearchService.index(saved);
        pushNotificationService.notifyTrainingCreated(saved.getTitle());
        return TrainingMapper.toDto(saved);
    }

    @CacheEvict(value = {"trainings", "trainings-all"}, allEntries = true)
    public TrainingResponseDTO update(Long id, @Valid TrainingRequestDTO req) {
        Training existing = trainingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Training not found"));
        existing.setTitle(req.getTitle());
        existing.setDescription(req.getDescription());
        existing.setCategory(req.getCategory());
        existing.setLevel(req.getLevel());
        existing.setPrice(req.getPrice());
        existing.setThumbnailUrl(req.getThumbnailUrl());
        existing.setLanguage(req.getLanguage());
        existing.setStatus(req.getStatus());
        trainingSearchService.index(existing);
        return TrainingMapper.toDto(existing);
    }

    @CacheEvict(value = {"trainings", "trainings-all"}, allEntries = true)
    public void delete(Long id) {
        trainingRepository.deleteById(id);
        trainingSearchService.deleteFromIndex(id);
    }

    public List<TrainingContentDTO> getContents(Long trainingId) {
        return contentRepository.findByTraining_Id(trainingId)
                .stream()
                .map(TrainingMapper::toDto)
                .collect(Collectors.toList());
    }

    public TrainingContentDTO addContent(Long trainingId, @Valid TrainingContentCreateDTO req) {
        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new EntityNotFoundException("Training not found"));
        TrainingContent content = new TrainingContent();
        content.setTraining(training);
        content.setTitle(req.getTitle());
        content.setContentUrl(req.getContentUrl());
        TrainingContent saved = contentRepository.save(content);
        return TrainingMapper.toDto(saved);
    }

    public void deleteContent(Long contentId) {
        contentRepository.deleteById(contentId);
    }

    public List<AvisDTO> getAvis(Long trainingId) {
        return avisRepository.findByTraining_IdOrderByCreatedAtDesc(trainingId)
                .stream()
                .map(TrainingMapper::toDto)
                .collect(Collectors.toList());
    }

    public AvisDTO addAvis(Long trainingId, @Valid AvisCreateDTO req) {
        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new EntityNotFoundException("Training not found"));
        Avis avis = new Avis();
        avis.setTraining(training);
        avis.setAuthorName(req.getAuthorName());
        avis.setRating(req.getRating());
        avis.setComment(req.getComment());
        Avis saved = avisRepository.save(avis);
        pushNotificationService.notifyReviewAdded(training.getTitle());
        return TrainingMapper.toDto(saved);
    }

    public void deleteAvis(Long avisId) {
        avisRepository.deleteById(avisId);
    }
}

