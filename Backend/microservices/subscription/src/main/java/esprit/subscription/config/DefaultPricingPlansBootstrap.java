package esprit.subscription.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures at least pricing plan {@code id = 1} exists so Stripe success URLs using {@code planId=1} work.
 * Runs after JPA schema init (high order).
 */
@Component
@Order(100)
public class DefaultPricingPlansBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultPricingPlansBootstrap.class);

    private final JdbcTemplate jdbcTemplate;

    public DefaultPricingPlansBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer tables = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*) FROM information_schema.tables
                            WHERE table_schema = DATABASE() AND table_name = 'pricing_plans'
                            """,
                    Integer.class);
            if (tables == null || tables == 0) {
                log.debug("pricing_plans table missing — Hibernate may create it on next use.");
                return;
            }

            jdbcTemplate.update(
                    """
                            INSERT INTO pricing_plans (id, name, description, monthly_price, yearly_price, features, is_active, highlight)
                            VALUES (1, 'ENTERPRISE', 'Default enterprise subscription', 29.99, 299.99, '', 1, 0)
                            ON DUPLICATE KEY UPDATE id = id
                            """);

            log.info("Ensured pricing_plans id=1 (ENTERPRISE) exists for payment confirm.");
        } catch (Exception e) {
            log.warn("Could not seed default pricing plan id=1: {}", e.getMessage());
        }
    }
}
