package esprit.subscription.elasticsearch;

import esprit.subscription.entity.PricingPlan;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

/** Elasticsearch-only repo (separate package avoids JPA scanning the same interface). */
public interface PricingPlanSearchRepository extends ElasticsearchRepository<PricingPlan, String> {

    List<PricingPlan> findByNameContainingOrDescriptionContaining(String a, String b);
}
