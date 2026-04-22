package tn.esprit.training.chat.dto;

import java.util.List;
import java.util.Map;

public class ChatResponse {
    private String answer;
    private String intent;
    private List<Map<String, Object>> items;

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public List<Map<String, Object>> getItems() {
        return items;
    }

    public void setItems(List<Map<String, Object>> items) {
        this.items = items;
    }
}

