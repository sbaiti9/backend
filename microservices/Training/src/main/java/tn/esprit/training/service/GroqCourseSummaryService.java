package tn.esprit.training.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GroqCourseSummaryService {

    private final GroqClientService groq;

    public GroqCourseSummaryService(GroqClientService groq) {
        this.groq = groq;
    }

    public String summarize(String courseTitle, String description, List<String> lessonTitles) {
        String lessons = lessonTitles.stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(", "));

        String prompt =
                "Write a concise course summary (2-4 sentences) for learners.\n"
                        + "Course title: " + courseTitle + "\n"
                        + "Description: " + description + "\n"
                        + "Lesson titles: " + lessons + "\n"
                        + "Do not repeat the title only; synthesize what the learner will gain.";

        return groq.chat(prompt);
    }
}

