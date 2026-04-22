package esprit.subscription.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adds {@code auto_renew} to {@code subscriptions} when the table predates the field.
 * Hibernate {@code ddl-auto=update} may not add columns to existing MySQL tables, which causes
 * {@code Unknown column 'auto_renew' in 'field list'} on any query.
 * <p>
 * Disable with {@code skillio.db.auto-fix-subscriptions-auto-renew=false}.
 */
@Component
@Order(2)
public class SubscriptionsAutoRenewColumnFix implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionsAutoRenewColumnFix.class);

    private final JdbcTemplate jdbcTemplate;

    @Value("${skillio.db.auto-fix-subscriptions-auto-renew:true}")
    private boolean enabled;

    public SubscriptionsAutoRenewColumnFix(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.debug("subscriptions.auto_renew auto-fix disabled");
            return;
        }
        try {
            Integer tables = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*) FROM information_schema.tables
                            WHERE table_schema = DATABASE() AND table_name = 'subscriptions'
                            """,
                    Integer.class);
            if (tables == null || tables == 0) {
                log.debug("Table subscriptions does not exist yet; Hibernate will create it.");
                return;
            }

            Integer col = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*) FROM information_schema.columns
                            WHERE table_schema = DATABASE() AND table_name = 'subscriptions'
                              AND column_name = 'auto_renew'
                            """,
                    Integer.class);
            if (col != null && col > 0) {
                log.debug("subscriptions.auto_renew already present.");
                return;
            }

            log.warn("subscriptions.auto_renew missing — applying ALTER TABLE ADD COLUMN.");
            jdbcTemplate.execute(
                    "ALTER TABLE subscriptions ADD COLUMN auto_renew TINYINT(1) NOT NULL DEFAULT 0");
            log.info("subscriptions.auto_renew added — payment confirm queries should work.");
        } catch (Exception e) {
            log.error("Could not add subscriptions.auto_renew: {}", e.getMessage());
        }
    }
}
