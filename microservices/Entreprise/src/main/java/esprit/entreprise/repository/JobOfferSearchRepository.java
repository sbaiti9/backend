package esprit.entreprise.repository;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import esprit.entreprise.entity.JobOffer;

import java.util.List;

public interface JobOfferSearchRepository extends ElasticsearchRepository<JobOffer, String> {
    List<JobOffer> findByNameContainingOrDescriptionContaining(String a, String b);
}

