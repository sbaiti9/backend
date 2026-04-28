package esprit.subscription.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Logs a clear warning when Groq is enabled but no key is configured.
 * This helps diagnose "Groq indisponible" issues quickly in local runs.
 */
@Component
public class GroqConfigSanityCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GroqConfigSanityCheck.class);

    private final GroqProperties props;

    public GroqConfigSanityCheck(GroqProperties props) {
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.isEnabled()) {
            log.info("Groq disabled (groq.api.enabled=false).");
            return;
        }
        String key = props.getKey();
        if (key == null || key.isBlank()) {
            log.warn("Groq enabled but API key is missing. Set env var GROQ_API_KEY (recommended) or configure groq.api.key, then restart subscription service.");
        } else {
            log.info("Groq enabled and API key detected (length={} chars).", key.trim().length());
        }
    }
}

