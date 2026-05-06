package esprit.subscription.repository;

import esprit.subscription.entity.PricingPlan;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface PricingPlanSearchRepository extends ElasticsearchRepository<PricingPlan, String> {
    List<PricingPlan> findByNameContainingOrDescriptionContaining(String a, String b);
}

