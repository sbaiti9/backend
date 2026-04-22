package tn.esprit.training.dto;

import java.util.ArrayList;
import java.util.List;

public class QuizResponseDTO {
    private List<QuizQuestionDTO> questions = new ArrayList<>();
    private String message;

    public QuizResponseDTO() {}

    public QuizResponseDTO(List<QuizQuestionDTO> questions, String message) {
        this.questions = questions != null ? questions : new ArrayList<>();
        this.message = message;
    }

    public List<QuizQuestionDTO> getQuestions() {
        return questions;
    }

    public void setQuestions(List<QuizQuestionDTO> questions) {
        this.questions = questions != null ? questions : new ArrayList<>();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

