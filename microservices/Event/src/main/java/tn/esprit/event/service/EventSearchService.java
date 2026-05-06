package tn.esprit.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.event.entity.Event;
import tn.esprit.event.repository.EventSearchRepository;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventSearchService {
    private final EventSearchRepository searchRepo;

    public void index(Event event) {
        if (event == null || event.getId() == null) return;

        event.setEsId(String.valueOf(event.getId()));
        event.setName(event.getTitle());
        event.setDescription(buildDescription(event));

        searchRepo.save(event);
    }

    public List<Event> search(String q) {
        if (q == null) q = "";
        q = q.trim();
        if (q.isEmpty()) return List.of();
        return searchRepo.findByNameContainingOrDescriptionContaining(q, q);
    }

    public void deleteFromIndex(Long id) {
        if (id == null) return;
        searchRepo.deleteById(String.valueOf(id));
    }

    private String buildDescription(Event e) {
        StringBuilder sb = new StringBuilder();
        append(sb, e.getLocation());
        if (e.getStartDate() != null) append(sb, e.getStartDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        if (e.getEndDate() != null) append(sb, e.getEndDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        if (e.getUserId() != null) append(sb, "organizer:" + e.getUserId());
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

