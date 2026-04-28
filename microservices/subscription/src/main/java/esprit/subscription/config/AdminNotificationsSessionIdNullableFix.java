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
 * Older DBs may have {@code admin_notifications.session_id NOT NULL}, which breaks
 * {@code USER_BLOCKED} and {@code ADMIN_UI_TEST} inserts. Hibernate {@code ddl-auto=update}
 * does not always relax nullability on MySQL.
 * <p>
 * Disable with {@code skillio.db.auto-fix-admin-notifications-session-id-nullable=false}.
 */
@Component
@Order(3)
public class AdminNotificationsSessionIdNullableFix implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminNotificationsSessionIdNullableFix.class);

    private final JdbcTemplate jdbcTemplate;

    @Value("${skillio.db.auto-fix-admin-notifications-session-id-nullable:true}")
    private boolean enabled;

    public AdminNotificationsSessionIdNullableFix(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.debug("admin_notifications.session_id nullable auto-fix disabled");
            return;
        }
        try {
            Integer tables = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*) FROM information_schema.tables
                            WHERE table_schema = DATABASE() AND table_name = 'admin_notifications'
                            """,
                    Integer.class);
            if (tables == null || tables == 0) {
                log.debug("Table admin_notifications does not exist yet; Hibernate will create it.");
                return;
            }

            String nullable = jdbcTemplate.queryForObject(
                    """
                            SELECT IS_NULLABLE FROM information_schema.columns
                            WHERE table_schema = DATABASE() AND table_name = 'admin_notifications'
                              AND column_name = 'session_id'
                            """,
                    String.class);
            if (nullable == null) {
                log.debug("admin_notifications.session_id column missing; skip.");
                return;
            }
            if ("YES".equalsIgnoreCase(nullable)) {
                log.debug("admin_notifications.session_id already nullable.");
                return;
            }

            log.warn("admin_notifications.session_id is NOT NULL — applying ALTER COLUMN ... NULL.");
            jdbcTemplate.execute("ALTER TABLE admin_notifications MODIFY COLUMN session_id BIGINT NULL");
            log.info("admin_notifications.session_id is now nullable (USER_BLOCKED / UI test inserts).");
        } catch (Exception e) {
            log.error("Could not alter admin_notifications.session_id to nullable: {}", e.getMessage());
        }
    }
}
