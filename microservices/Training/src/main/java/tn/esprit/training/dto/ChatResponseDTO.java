package tn.esprit.training.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class ChatResponseDTO {
    @JsonProperty("answer")
    private String reply;
    private String intent;
    private String sqlGenerated;
    private List<Map<String, Object>> data;

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getSqlGenerated() {
        return sqlGenerated;
    }

    public void setSqlGenerated(String sqlGenerated) {
        this.sqlGenerated = sqlGenerated;
    }

    public List<Map<String, Object>> getData() {
        return data;
    }

    public void setData(List<Map<String, Object>> data) {
        this.data = data;
    }
}

