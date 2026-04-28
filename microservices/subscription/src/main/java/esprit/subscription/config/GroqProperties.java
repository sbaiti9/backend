package esprit.subscription.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "groq.api")
public class GroqProperties {

    private boolean enabled = true;
    private String url = "https://api.groq.com/openai/v1/chat/completions";
    /** Prefer env var {@code GROQ_API_KEY}; never commit real keys. */
    private String key = "";
    private String model = "llama-3.1-8b-instant";

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
