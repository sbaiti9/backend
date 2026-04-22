package tn.esprit.training.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.training.dto.ChatResponseDTO;
import tn.esprit.training.entity.Training;
import tn.esprit.training.repository.AvisRepository;
import tn.esprit.training.repository.PlatformSnapshotJdbcRepository;
import tn.esprit.training.repository.TrainingContentRepository;
import tn.esprit.training.repository.TrainingRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    @Mock TrainingRepository trainingRepository;
    @Mock TrainingContentRepository contentRepository;
    @Mock AvisRepository avisRepository;
    @Mock PlatformSnapshotJdbcRepository platformJdbc;
    @Mock AnthropicClientService anthropicClientService;
    @Mock GroqClientService groqClientService;

    private AiChatService svc;

    @BeforeEach
    void setUp() {
        svc = new AiChatService(
                trainingRepository,
                contentRepository,
                avisRepository,
                platformJdbc,
                anthropicClientService,
                groqClientService
        );
    }

    @Test
    void greetingIncludesFormattedDisplayName_whenProvided() throws Exception {
        when(trainingRepository.findAll()).thenReturn(List.of(
                mkTraining(1L, "T1"),
                mkTraining(2L, "T2")
        ));
        when(platformJdbc.companiesCount()).thenReturn(2L);
        when(platformJdbc.jobOffersCount()).thenReturn(9L);
        when(platformJdbc.eventsCount()).thenReturn(3L);
        when(platformJdbc.pricingPlansCount()).thenReturn(1L);
        when(platformJdbc.subscriptionsCount()).thenReturn(0L);

        ChatResponseDTO resp = svc.chat("hi", "debbich amine");
        assertThat(resp.getIntent()).isEqualTo("PLATFORM_QUICK");
        assertThat(resp.getReply()).startsWith("Hello, Debbich Amine!");
        assertThat(resp.getReply()).contains("2 companie(s)");
    }

    @Test
    void greetingFallsBackToHello_whenNameIsEmptyOrUnsafe() throws Exception {
        when(trainingRepository.findAll()).thenReturn(List.of(mkTraining(1L, "T1")));
        when(platformJdbc.companiesCount()).thenReturn(0L);
        when(platformJdbc.jobOffersCount()).thenReturn(0L);
        when(platformJdbc.eventsCount()).thenReturn(0L);
        when(platformJdbc.pricingPlansCount()).thenReturn(0L);
        when(platformJdbc.subscriptionsCount()).thenReturn(0L);

        ChatResponseDTO resp = svc.chat("bonjour", "   <script>alert(1)</script>   ");
        // sanitizeDisplayName keeps letters/digits/spaces/hyphen/apostrophe, so this becomes "script alert 1 script"
        assertThat(resp.getReply()).startsWith("Hello, Script Alert 1 Script!");
    }

    private static Training mkTraining(Long id, String title) {
        Training t = new Training();
        t.setId(id);
        t.setTitle(title);
        t.setCategory("Cat");
        t.setLevel("Beginner");
        t.setPrice(BigDecimal.valueOf(10));
        t.setLanguage("EN");
        t.setStatus("PUBLISHED");
        t.setDescription("Desc");
        return t;
    }
}

