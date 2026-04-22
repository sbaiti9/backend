package tn.esprit.training.dto;

import jakarta.validation.constraints.NotBlank;

public class LearningPathRequestDTO {
    @NotBlank
    private String goal;

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }
}

