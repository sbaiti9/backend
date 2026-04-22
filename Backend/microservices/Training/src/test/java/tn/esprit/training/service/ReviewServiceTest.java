package tn.esprit.training.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.training.dto.AvisCreateDTO;
import tn.esprit.training.entity.Avis;
import tn.esprit.training.entity.Training;
import tn.esprit.training.repository.AvisRepository;
import tn.esprit.training.repository.TrainingRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock TrainingRepository trainingRepository;
    @Mock AvisRepository avisRepository;

    private ReviewService svc;

    @BeforeEach
    void setUp() {
        svc = new ReviewService(trainingRepository, avisRepository);
    }

    @Test
    void addReviewSetsIdAndSavesAvis() {
        long trainingId = 50L;
        Training t = new Training();
        t.setId(trainingId);
        t.setTitle("X");
        when(trainingRepository.findById(trainingId)).thenReturn(Optional.of(t));
        when(avisRepository.nextId()).thenReturn(123L);

        AvisCreateDTO dto = new AvisCreateDTO();
        dto.setAuthorName("Test User");
        dto.setRating(5);
        dto.setComment("hello!");

        when(avisRepository.save(any(Avis.class))).thenAnswer(inv -> inv.getArgument(0));

        svc.addReview(trainingId, dto);

        ArgumentCaptor<Avis> cap = ArgumentCaptor.forClass(Avis.class);
        verify(avisRepository).save(cap.capture());
        Avis saved = cap.getValue();

        assertThat(saved.getId()).isEqualTo(123L);
        assertThat(saved.getTraining()).isSameAs(t);
        assertThat(saved.getAuthorName()).isEqualTo("Test User");
        assertThat(saved.getRating()).isEqualTo(5);
        assertThat(saved.getComment()).isEqualTo("hello!");
    }

    @Test
    void addReviewThrowsWhenTrainingMissing() {
        when(trainingRepository.findById(999L)).thenReturn(Optional.empty());
        AvisCreateDTO dto = new AvisCreateDTO();
        dto.setAuthorName("A");
        dto.setRating(1);
        dto.setComment("hello!");

        assertThatThrownBy(() -> svc.addReview(999L, dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Training not found");
    }
}

