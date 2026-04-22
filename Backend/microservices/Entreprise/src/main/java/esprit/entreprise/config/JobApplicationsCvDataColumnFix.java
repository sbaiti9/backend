package esprit.entreprise.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures {@code job_applications.cv_data} is large enough for CV files.
 * Some MySQL schemas created before the CV upload used a small VARBINARY/BLOB,
 * causing "Data too long for column 'cv_data'".
 * <p>
 * Disable with {@code skillio.db.auto-fix-job-applications-cv-data=false}.
 */
@Component
@Order(3)
public class JobApplicationsCvDataColumnFix implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JobApplicationsCvDataColumnFix.class);

    private final JdbcTemplate jdbcTemplate;

    @Value("${skillio.db.auto-fix-job-applications-cv-data:true}")
    private boolean enabled;

    public JobApplicationsCvDataColumnFix(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.debug("job_applications.cv_data auto-fix disabled");
            return;
        }
        try {
            Integer tables = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*) FROM information_schema.tables
                            WHERE table_schema = DATABASE() AND table_name = 'job_applications'
                            """,
                    Integer.class);
            if (tables == null || tables == 0) {
                log.debug("Table job_applications does not exist yet; Hibernate will create it.");
                return;
            }

            String type = jdbcTemplate.queryForObject(
                    """
                            SELECT DATA_TYPE FROM information_schema.columns
                            WHERE table_schema = DATABASE()
                              AND table_name = 'job_applications'
                              AND column_name = 'cv_data'
                            """,
                    String.class);

            if (type != null && type.equalsIgnoreCase("longblob")) {
                log.debug("job_applications.cv_data already LONG_BLOB.");
                return;
            }

            log.warn("job_applications.cv_data is '{}' — applying ALTER TABLE to LONGBLOB.", type);
            jdbcTemplate.execute("ALTER TABLE job_applications MODIFY COLUMN cv_data LONGBLOB");
            log.info("job_applications.cv_data migrated to LONGBLOB.");
        } catch (Exception e) {
            log.error("Could not modify job_applications.cv_data: {}", e.getMessage());
        }
    }
}

