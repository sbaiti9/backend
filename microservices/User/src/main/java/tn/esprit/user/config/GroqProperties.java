package tn.esprit.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "skillio.groq")
public class GroqProperties {
    /**
     * Toggle Groq AI summary generation.
     */
    private boolean enabled = false;

    /**
     * OpenAI-compatible chat completions endpoint.
     */
    private String url = "https://api.groq.com/openai/v1/chat/completions";

    /**
     * Groq API key (Bearer).
     */
    private String key;

    /**
     * Model name. Example: llama-3.3-70b-versatile
     */
    private String model = "llama-3.3-70b-versatile";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}

