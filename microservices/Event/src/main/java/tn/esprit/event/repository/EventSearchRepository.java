package tn.esprit.event.repository;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import tn.esprit.event.entity.Event;

import java.util.List;

public interface EventSearchRepository extends ElasticsearchRepository<Event, String> {
    List<Event> findByNameContainingOrDescriptionContaining(String a, String b);
}

