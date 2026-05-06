package tn.esprit.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.user.entity.Users;
import tn.esprit.user.repository.UsersSearchRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final UsersSearchRepository searchRepo;

    public void indexUser(Users user) {
        if (user == null || user.getId() == null) return;

        // Ensure ES id and derived description are present before indexing
        user.setEsId(String.valueOf(user.getId()));
        user.setDescription(buildDescription(user));

        searchRepo.save(user);
    }

    public void deleteFromIndex(Long id) {
        if (id == null) return;
        searchRepo.deleteById(String.valueOf(id));
    }

    public List<Users> search(String q) {
        if (q == null) q = "";
        q = q.trim();
        if (q.isEmpty()) return List.of();
        return searchRepo.findByNameContainingOrDescriptionContaining(q, q);
    }

    private String buildDescription(Users u) {
        StringBuilder sb = new StringBuilder();
        append(sb, u.getPrenom());
        append(sb, u.getProfession());
        append(sb, u.getSpecialte());
        append(sb, u.getAdresse());
        append(sb, u.getEmail());
        return sb.toString().trim();
    }

    private void append(StringBuilder sb, String v) {
        if (v == null) return;
        String t = v.trim();
        if (t.isEmpty()) return;
        if (!sb.isEmpty()) sb.append(' ');
        sb.append(t);
    }
}

