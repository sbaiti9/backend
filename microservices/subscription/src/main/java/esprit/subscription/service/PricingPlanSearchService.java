package esprit.subscription.service;

import esprit.subscription.entity.PricingPlan;
import esprit.subscription.elasticsearch.PricingPlanSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PricingPlanSearchService {
    private final PricingPlanSearchRepository searchRepo;

    public void index(PricingPlan plan) {
        if (plan == null || plan.getId() == null) return;
        plan.setEsId(String.valueOf(plan.getId()));
        searchRepo.save(plan);
    }

    public List<PricingPlan> search(String q) {
        if (q == null) q = "";
        q = q.trim();
        if (q.isEmpty()) return List.of();
        return searchRepo.findByNameContainingOrDescriptionContaining(q, q);
    }

    public void deleteFromIndex(Long id) {
        if (id == null) return;
        searchRepo.deleteById(String.valueOf(id));
    }
}

