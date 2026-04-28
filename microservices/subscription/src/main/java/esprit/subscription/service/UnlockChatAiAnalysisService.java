package esprit.subscription.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Schedules Groq analysis after user messages (async so HTTP response is not blocked).
 */
@Service
public class UnlockChatAiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(UnlockChatAiAnalysisService.class);

    private final UnlockChatAiProcessor processor;

    public UnlockChatAiAnalysisService(UnlockChatAiProcessor processor) {
        this.processor = processor;
    }

    @Async
    public void scheduleAnalysis(Long sessionId) {
        try {
            processor.process(sessionId, false);
        } catch (Exception e) {
            log.warn("Unlock chat AI analysis failed for session {}: {}", sessionId, e.getMessage());
        }
    }

    /** Admin “forcer l’analyse” — higher temperature + unique prompt suffix so scores are not stuck on one value. */
    @Async
    public void scheduleForcedAnalysis(Long sessionId) {
        try {
            processor.process(sessionId, true);
        } catch (Exception e) {
            log.warn("Unlock chat forced AI analysis failed for session {}: {}", sessionId, e.getMessage());
        }
    }
}
