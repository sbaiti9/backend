package esprit.entreprise.service;

import esprit.entreprise.entity.ApplicationStatus;
import esprit.entreprise.entity.JobApplication;
import esprit.entreprise.entity.JobOffer;
import esprit.entreprise.repository.JobApplicationRepository;
import esprit.entreprise.repository.JobOfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

    @Mock JobApplicationRepository jobApplicationRepository;
    @Mock JobOfferRepository jobOfferRepository;

    @InjectMocks JobApplicationService svc;

    @BeforeEach
    void setUp() {
        // @InjectMocks handles wiring
    }

    @Test
    void createApplicationComputesMatchingScoreAndSaves() throws Exception {
        Long userId = 1L;
        Long offerId = 10L;

        when(jobApplicationRepository.existsByUserIdAndJobOfferId(userId, offerId)).thenReturn(false);
        JobOffer offer = new JobOffer();
        offer.setId(offerId);
        offer.setTitle("Angular Dev");
        when(jobOfferRepository.findById(offerId)).thenReturn(Optional.of(offer));

        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        JobApplication saved = svc.createApplication(
                userId,
                offerId,
                "Bac_plus_3",
                "ACTIVE",
                4.0,
                "Wissem",
                "w@x.com",
                "cover"
        );

        assertThat(saved.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(saved.getMatchingScore()).isNotNull();
        assertThat(saved.getMatchingScore()).isBetween(0, 100);

        ArgumentCaptor<JobApplication> cap = ArgumentCaptor.forClass(JobApplication.class);
        verify(jobApplicationRepository).save(cap.capture());
        assertThat(cap.getValue().getJobOffer()).isSameAs(offer);
    }

    @Test
    void createApplicationThrowsIfAlreadyApplied() {
        when(jobApplicationRepository.existsByUserIdAndJobOfferId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> svc.createApplication(
                1L, 10L, "Bac", "ACTIVE", 3.0, "X", "x@x.com", null
        )).isInstanceOf(Exception.class)
          .hasMessageContaining("déjà postulé");
    }
}

