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
 * Ensures {@code subscriptions.id} is AUTO_INCREMENT so JPA {@code GenerationType.IDENTITY} inserts work.
 * Hibernate {@code ddl-auto=update} often will not alter an existing non-AI column.
 * <p>
 * Disable with {@code skillio.db.auto-fix-subscriptions-id=false}.
 */
@Component
@Order(1)
public class SubscriptionsIdColumnFix implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionsIdColumnFix.class);

    private final JdbcTemplate jdbcTemplate;

    @Value("${skillio.db.auto-fix-subscriptions-id:true}")
    private boolean enabled;

    public SubscriptionsIdColumnFix(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.debug("subscriptions.id auto-fix disabled (skillio.db.auto-fix-subscriptions-id=false)");
            return;
        }
        try {
            Integer n = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*) FROM information_schema.tables
                            WHERE table_schema = DATABASE() AND table_name = 'subscriptions'
                            """,
                    Integer.class);
            if (n == null || n == 0) {
                log.debug("Table subscriptions does not exist yet; Hibernate will create it.");
                return;
            }

            String extra = jdbcTemplate.query(
                    """
                            SELECT EXTRA FROM information_schema.columns
                            WHERE table_schema = DATABASE() AND table_name = 'subscriptions' AND column_name = 'id'
                            """,
                    rs -> {
                        if (!rs.next()) {
                            return null;
                        }
                        return rs.getString("EXTRA");
                    });

            if (extra != null && extra.toLowerCase().contains("auto_increment")) {
                log.debug("subscriptions.id already AUTO_INCREMENT; nothing to do.");
                return;
            }

            log.warn("subscriptions.id is not AUTO_INCREMENT — applying ALTER TABLE (dev/local fix).");

            Integer pk = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*) FROM information_schema.table_constraints
                            WHERE table_schema = DATABASE() AND table_name = 'subscriptions'
                              AND constraint_type = 'PRIMARY KEY'
                            """,
                    Integer.class);

            if (pk == null || pk == 0) {
                jdbcTemplate.execute("ALTER TABLE subscriptions ADD PRIMARY KEY (id)");
                log.info("Added PRIMARY KEY on subscriptions");
            }

            jdbcTemplate.execute("ALTER TABLE subscriptions MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT");
            log.info("subscriptions.id is now AUTO_INCREMENT — payment confirm inserts should work.");
        } catch (Exception e) {
            log.error(
                    "Could not auto-fix subscriptions.id. Run manually in MySQL: see db/mysql-fix-subscriptions-id.sql — {}",
                    e.getMessage());
        }
    }
}
