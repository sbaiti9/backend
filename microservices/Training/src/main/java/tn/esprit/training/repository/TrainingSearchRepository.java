package tn.esprit.training.repository;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import tn.esprit.training.entity.Training;

import java.util.List;

public interface TrainingSearchRepository extends ElasticsearchRepository<Training, String> {
    List<Training> findByNameContainingOrDescriptionContaining(String a, String b);
}

