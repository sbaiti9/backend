package esprit.entreprise.service;

import esprit.entreprise.DTO.InterviewRequest;
import esprit.entreprise.entity.ApplicationStatus;
import esprit.entreprise.entity.Interview;
import esprit.entreprise.entity.JobApplication;
import esprit.entreprise.entity.JobOffer;
import esprit.entreprise.repository.InterviewCandidateFeedbackRepository;
import esprit.entreprise.repository.InterviewRepository;
import esprit.entreprise.repository.JobApplicationRepository;
import esprit.entreprise.repository.JobOfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    @Mock InterviewRepository interviewRepository;
    @Mock JobApplicationRepository jobApplicationRepository;
    @Mock JobOfferRepository jobOfferRepository;
    @Mock InterviewCandidateFeedbackRepository interviewCandidateFeedbackRepository;
    @Mock JavaMailSender mailSender;

    @InjectMocks InterviewService svc;

    @BeforeEach
    void setUp() {
        // @InjectMocks
    }

    @Test
    void createInterviewSetsShortlistedWhenNotAccepted_andSavesInterview() {
        Long appId = 5L;
        JobOffer offer = new JobOffer();
        offer.setId(99L);
        offer.setTitle("Backend");

        JobApplication app = JobApplication.builder()
                .id(appId)
                .userId(1L)
                .jobOffer(offer)
                .candidateName("Wissem")
                .candidateEmail("w@x.com")
                .status(ApplicationStatus.PENDING)
                .build();

        when(jobApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(interviewRepository.existsByApplicationId(appId)).thenReturn(false);
        when(interviewRepository.save(any(Interview.class))).thenAnswer(inv -> inv.getArgument(0));
        // mail sending errors are swallowed; keep mailSender mocked without behavior

        InterviewRequest req = new InterviewRequest();
        req.setApplicationId(appId);
        req.setInterviewDate(LocalDateTime.now().plusDays(1));
        req.setDurationMinutes(30);
        req.setType("ON_SITE");
        req.setLocation("Tunis");
        req.setNotes("note");

        var resp = svc.createInterview(req);

        // application should be set to SHORTLISTED (was not ACCEPTED)
        verify(jobApplicationRepository).save(app);
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);

        verify(interviewRepository).save(any(Interview.class));
        assertThat(resp.getApplicationId()).isEqualTo(appId);
        assertThat(resp.getJobOfferId()).isEqualTo(offer.getId());
        assertThat(resp.getCandidateEmail()).isEqualTo("w@x.com");
    }

    @Test
    void createInterviewThrowsWhenAlreadyExists() {
        Long appId = 5L;
        JobOffer offer = new JobOffer();
        JobApplication app = JobApplication.builder()
                .id(appId)
                .jobOffer(offer)
                .status(ApplicationStatus.PENDING)
                .build();

        when(jobApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(interviewRepository.existsByApplicationId(appId)).thenReturn(true);

        InterviewRequest req = new InterviewRequest();
        req.setApplicationId(appId);
        req.setInterviewDate(LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> svc.createInterview(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("existe déjà");
    }
}

