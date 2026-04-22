package tn.esprit.training.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Read-only, best-effort snapshot of key platform tables in the shared MySQL database.
 * All queries are wrapped in safe fallbacks so missing tables/columns never break chat.
 */
@Repository
public class PlatformSnapshotJdbcRepository {
    private static final Logger log = LoggerFactory.getLogger(PlatformSnapshotJdbcRepository.class);
    private final JdbcTemplate jdbc;

    public PlatformSnapshotJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> companies(int limit) {
        // companies (id, name, industry, location, email, website)
        return firstSuccessfulList("companies", List.of(
                "SELECT id, name, industry, location, email, website FROM companies LIMIT ?",
                "SELECT id, name, industry, location, email, website FROM entreprise LIMIT ?",
                "SELECT id, name, industry, location, email, website FROM entreprises LIMIT ?",
                "SELECT id, nom AS name, secteur AS industry, adresse AS location, email, site_web AS website FROM entreprises LIMIT ?"
        ), limit);
    }

    public long companiesCount() {
        return firstSuccessfulCount("companiesCount", List.of(
                "SELECT COUNT(*) FROM companies",
                "SELECT COUNT(*) FROM entreprise",
                "SELECT COUNT(*) FROM entreprises"
        ));
    }

    public List<Map<String, Object>> jobOffersWithCompanyName(int limit) {
        // job_offers joined to companies for companyName (truncate long descriptions)
        return firstSuccessfulList("jobOffers", List.of(
                "SELECT jo.id, jo.title, LEFT(jo.description, 240) AS description, c.name AS companyName " +
                        "FROM job_offers jo LEFT JOIN companies c ON c.id = jo.company_id LIMIT ?",
                "SELECT jo.id, jo.title, LEFT(jo.description, 240) AS description, c.name AS companyName " +
                        "FROM job_offers jo LEFT JOIN companies c ON c.id = jo.companyId LIMIT ?",
                "SELECT jo.id, jo.title, LEFT(jo.description, 240) AS description, e.name AS companyName " +
                        "FROM job_offers jo LEFT JOIN entreprises e ON e.id = jo.entreprise_id LIMIT ?",
                "SELECT jo.id, jo.title, LEFT(jo.description, 240) AS description, e.name AS companyName " +
                        "FROM job_offers jo LEFT JOIN entreprises e ON e.id = jo.entrepriseId LIMIT ?"
        ), limit);
    }

    public long jobOffersCount() {
        return firstSuccessfulCount("jobOffersCount", List.of(
                "SELECT COUNT(*) FROM job_offers",
                "SELECT COUNT(*) FROM jobOffers",
                "SELECT COUNT(*) FROM job_offer",
                "SELECT COUNT(*) FROM joboffer"
        ));
    }

    public List<Map<String, Object>> events(int limit) {
        // events (id, title, dates, location, capacity, price) — avoid large blobs
        return firstSuccessfulList("events", List.of(
                "SELECT id, title, startDate, endDate, location, capacity, price FROM events LIMIT ?",
                "SELECT id, title, start_date AS startDate, end_date AS endDate, location, capacity, price FROM events LIMIT ?",
                "SELECT id, titre AS title, date_debut AS startDate, date_fin AS endDate, lieu AS location, capacite AS capacity, prix AS price FROM events LIMIT ?"
        ), limit);
    }

    public long eventsCount() {
        return firstSuccessfulCount("eventsCount", List.of(
                "SELECT COUNT(*) FROM events",
                "SELECT COUNT(*) FROM event",
                "SELECT COUNT(*) FROM evenements"
        ));
    }

    public List<Map<String, Object>> pricingPlans(int limit) {
        // pricing_plans (id, name, description trimmed, monthly/yearly price, isActive, highlight)
        return firstSuccessfulList("pricingPlans", List.of(
                "SELECT id, name, LEFT(description, 240) AS description, monthlyPrice, yearlyPrice, isActive, highlight FROM pricing_plans LIMIT ?",
                "SELECT id, name, LEFT(description, 240) AS description, monthly_price AS monthlyPrice, yearly_price AS yearlyPrice, is_active AS isActive, highlight FROM pricing_plans LIMIT ?",
                "SELECT id, name, LEFT(description, 240) AS description, monthly_price AS monthlyPrice, yearly_price AS yearlyPrice, active AS isActive, highlight FROM pricing_plans LIMIT ?",
                "SELECT id, nom AS name, LEFT(description, 240) AS description, prix_mensuel AS monthlyPrice, prix_annuel AS yearlyPrice, actif AS isActive, highlight FROM pricing_plans LIMIT ?"
        ), limit);
    }

    public long pricingPlansCount() {
        return firstSuccessfulCount("pricingPlansCount", List.of(
                "SELECT COUNT(*) FROM pricing_plans",
                "SELECT COUNT(*) FROM pricingPlans",
                "SELECT COUNT(*) FROM plans_tarification"
        ));
    }

    public List<Map<String, Object>> subscriptionsByStatus() {
        // aggregates only, no per-user PII
        return firstSuccessfulListNoArgs("subscriptionsByStatus", List.of(
                "SELECT status, COUNT(*) AS count FROM subscriptions GROUP BY status",
                "SELECT status, COUNT(*) AS count FROM subscription GROUP BY status",
                "SELECT statut AS status, COUNT(*) AS count FROM subscriptions GROUP BY statut"
        ));
    }

    public long subscriptionsCount() {
        return firstSuccessfulCount("subscriptionsCount", List.of(
                "SELECT COUNT(*) FROM subscriptions",
                "SELECT COUNT(*) FROM subscription"
        ));
    }

    public List<Map<String, Object>> userCountsByRole() {
        // aggregates only; try table names users then Users; role columns may vary
        return firstSuccessfulListNoArgs("userCountsByRole", List.of(
                "SELECT role, COUNT(*) AS count FROM users GROUP BY role",
                "SELECT role, COUNT(*) AS count FROM Users GROUP BY role",
                "SELECT user_role AS role, COUNT(*) AS count FROM users GROUP BY user_role",
                "SELECT userRole AS role, COUNT(*) AS count FROM users GROUP BY userRole"
        ));
    }

    public long usersCount() {
        return firstSuccessfulCount("usersCount", List.of(
                "SELECT COUNT(*) FROM users",
                "SELECT COUNT(*) FROM Users"
        ));
    }

    private List<Map<String, Object>> firstSuccessfulList(String label, List<String> sqls, int limit) {
        List<Exception> failures = new ArrayList<>();
        for (String sql : sqls) {
            try {
                return jdbc.queryForList(sql, limit);
            } catch (Exception e) {
                failures.add(e);
            }
        }
        if (!failures.isEmpty()) {
            log.debug("PlatformSnapshot query '{}' failed for all candidates ({}). Returning empty list.",
                    label, failures.get(0).getMessage());
        }
        return List.of();
    }

    private List<Map<String, Object>> firstSuccessfulListNoArgs(String label, List<String> sqls) {
        List<Exception> failures = new ArrayList<>();
        for (String sql : sqls) {
            try {
                return jdbc.queryForList(sql);
            } catch (Exception e) {
                failures.add(e);
            }
        }
        if (!failures.isEmpty()) {
            log.debug("PlatformSnapshot query '{}' failed for all candidates ({}). Returning empty list.",
                    label, failures.get(0).getMessage());
        }
        return List.of();
    }

    private long firstSuccessfulCount(String label, List<String> sqls) {
        List<Exception> failures = new ArrayList<>();
        for (String sql : sqls) {
            try {
                Long v = jdbc.queryForObject(sql, Long.class);
                return v != null ? v : 0L;
            } catch (Exception e) {
                failures.add(e);
            }
        }
        if (!failures.isEmpty()) {
            log.debug("PlatformSnapshot count '{}' failed for all candidates ({}). Returning 0.",
                    label, failures.get(0).getMessage());
        }
        return 0L;
    }
}

