package tn.esprit.training.chat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class AvisRepositoryJdbc {
    private final JdbcTemplate jdbc;

    public AvisRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> reviewsForTitleLike(String title) {
        String like = "%" + (title == null ? "" : title.trim()) + "%";
        String sql = "SELECT a.id, a.rating, a.comment, a.author_name, a.created_at " +
                "FROM avis a JOIN training t ON t.id = a.training_id " +
                "WHERE t.title LIKE ? ORDER BY a.id DESC LIMIT 10";
        return jdbc.queryForList(sql, like);
    }
}

