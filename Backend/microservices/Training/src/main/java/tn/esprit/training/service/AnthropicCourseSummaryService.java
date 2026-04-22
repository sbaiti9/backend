package tn.esprit.training.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AnthropicCourseSummaryService {

    private final AnthropicClientService anthropic;

    public AnthropicCourseSummaryService(AnthropicClientService anthropic) {
        this.anthropic = anthropic;
    }

    public String summarize(String courseTitle, String description, List<String> lessonTitles) {
        String lessons = lessonTitles.stream().map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.joining(", "));
        String prompt = "Write a concise course summary (2-4 sentences) for learners. Course title: " + courseTitle
                + "\nDescription: " + description
                + "\nLesson titles: " + lessons
                + "\nDo not repeat the title only; synthesize what the learner will gain.";
        return anthropic.callClaude(prompt);
    }
}

