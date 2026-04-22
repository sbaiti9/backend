package tn.esprit.training.dto;

import java.util.ArrayList;
import java.util.List;

public class QuizQuestionDTO {
    private String question;
    private List<String> options = new ArrayList<>();
    /** 0-based index of the correct option */
    private int correctIndex;

    public QuizQuestionDTO() {}

    public QuizQuestionDTO(String question, List<String> options, int correctIndex) {
        this.question = question;
        this.options = options != null ? options : new ArrayList<>();
        this.correctIndex = correctIndex;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options != null ? options : new ArrayList<>();
    }

    public int getCorrectIndex() {
        return correctIndex;
    }

    public void setCorrectIndex(int correctIndex) {
        this.correctIndex = correctIndex;
    }
}

