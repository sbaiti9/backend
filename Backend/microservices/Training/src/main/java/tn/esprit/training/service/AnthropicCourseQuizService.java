package tn.esprit.training.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import tn.esprit.training.dto.QuizQuestionDTO;

import java.util.ArrayList;
import java.util.List;

@Service
public class AnthropicCourseQuizService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int QUIZ_MAX_TOKENS = 4096;

    private final AnthropicClientService anthropic;

    public AnthropicCourseQuizService(AnthropicClientService anthropic) {
        this.anthropic = anthropic;
    }

    public List<QuizQuestionDTO> generateQuiz(String courseTitle, String description, List<String> lessonTitles) {
        StringBuilder sb = new StringBuilder();
        for (String t : lessonTitles) {
            if (t != null && !t.isBlank()) sb.append("- ").append(t.trim()).append("\n");
        }
        String prompt = "Create exactly 5 multiple-choice quiz questions about this course. Respond with ONLY a JSON array (no markdown), "
                + "each element: {\"question\":\"...\",\"options\":[\"A\",\"B\",\"C\",\"D\"],\"correctIndex\":0} "
                + "correctIndex is 0-based. Course: " + courseTitle + "\nDescription: " + description + "\nLessons:\n" + sb;
        String raw = anthropic.callClaude(prompt, QUIZ_MAX_TOKENS);
        return parseQuestions(raw);
    }

    private static List<QuizQuestionDTO> parseQuestions(String raw) {
        List<QuizQuestionDTO> out = new ArrayList<>();
        try {
            String json = extractJsonArray(raw.trim());
            JsonNode arr = MAPPER.readTree(json);
            if (arr.isArray()) {
                for (JsonNode q : arr) {
                    String question = q.path("question").asText("");
                    List<String> options = new ArrayList<>();
                    JsonNode opts = q.path("options");
                    if (opts.isArray()) {
                        for (JsonNode o : opts) {
                            options.add(o.asText(""));
                        }
                    }
                    int correct = q.path("correctIndex").asInt(0);
                    if (!question.isBlank() && options.size() >= 2) {
                        out.add(new QuizQuestionDTO(question, options, correct));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String extractJsonArray(String s) {
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }
}

