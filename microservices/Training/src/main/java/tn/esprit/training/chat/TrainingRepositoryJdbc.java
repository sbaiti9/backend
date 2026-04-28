package tn.esprit.training.chat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class TrainingRepositoryJdbc {
    private final JdbcTemplate jdbc;

    public TrainingRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> topRated() {
        String sql = "SELECT t.id, t.title, COALESCE(AVG(a.rating),0) AS avg_rating, COUNT(a.id) AS nb_reviews " +
                "FROM training t LEFT JOIN avis a ON a.training_id = t.id " +
                "GROUP BY t.id, t.title ORDER BY avg_rating DESC, nb_reviews DESC LIMIT 10";
        return jdbc.queryForList(sql);
    }

    public List<Map<String, Object>> mostReviewed() {
        String sql = "SELECT t.id, t.title, COUNT(a.id) AS nb_reviews " +
                "FROM training t LEFT JOIN avis a ON a.training_id = t.id " +
                "GROUP BY t.id, t.title ORDER BY nb_reviews DESC LIMIT 10";
        return jdbc.queryForList(sql);
    }

    public List<Map<String, Object>> cheapest() {
        String sql = "SELECT id, title, price FROM training ORDER BY price ASC LIMIT 10";
        return jdbc.queryForList(sql);
    }

    public List<Map<String, Object>> mostExpensive() {
        String sql = "SELECT id, title, price FROM training ORDER BY price DESC LIMIT 10";
        return jdbc.queryForList(sql);
    }

    public List<Map<String, Object>> noReviews() {
        String sql = "SELECT t.id, t.title FROM training t LEFT JOIN avis a ON a.training_id = t.id " +
                "WHERE a.id IS NULL LIMIT 10";
        return jdbc.queryForList(sql);
    }

    public List<Map<String, Object>> searchCourses(String q) {
        String like = "%" + (q == null ? "" : q.trim()) + "%";
        String sql = "SELECT id, title, price, level FROM training " +
                "WHERE title LIKE ? OR description LIKE ? LIMIT 10";
        return jdbc.queryForList(sql, like, like);
    }
}

