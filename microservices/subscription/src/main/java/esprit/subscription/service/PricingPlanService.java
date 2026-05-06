package esprit.subscription.service;

import esprit.subscription.entity.PricingPlan;
import esprit.subscription.repository.PricingPlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PricingPlanService {

    @Autowired
    private PricingPlanRepository pricingPlanRepository;

    @Autowired
    private PricingPlanSearchService pricingPlanSearchService;

    @Cacheable(value = "pricingPlans-active", key = "'all'")
    public List<PricingPlan> findAllActive() {
        return pricingPlanRepository.findByIsActiveTrue();
    }

    @Cacheable(value = "pricingPlans-all", key = "'all'")
    public List<PricingPlan> findAll() {
        return pricingPlanRepository.findAll();
    }

    @Cacheable(value = "pricingPlans", key = "#id")
    public Optional<PricingPlan> findById(Long id) {
        return pricingPlanRepository.findById(id);
    }

    @CacheEvict(value = {"pricingPlans", "pricingPlans-active", "pricingPlans-all"}, allEntries = true)
    public PricingPlan save(PricingPlan pricingPlan) {
        if (pricingPlan.getIsActive() == null) {
            pricingPlan.setIsActive(true);
        }
        PricingPlan saved = pricingPlanRepository.save(pricingPlan);
        pricingPlanSearchService.index(saved);
        return saved;
    }

    @CacheEvict(value = {"pricingPlans", "pricingPlans-active", "pricingPlans-all"}, allEntries = true)
    public PricingPlan update(Long id, PricingPlan pricingPlanDetails) {
        Optional<PricingPlan> optionalPlan = pricingPlanRepository.findById(id);
        if (optionalPlan.isPresent()) {
            PricingPlan pricingPlan = optionalPlan.get();
            pricingPlan.setName(pricingPlanDetails.getName());
            pricingPlan.setDescription(pricingPlanDetails.getDescription());
            pricingPlan.setMonthlyPrice(pricingPlanDetails.getMonthlyPrice());
            pricingPlan.setYearlyPrice(pricingPlanDetails.getYearlyPrice());
            pricingPlan.setFeatures(pricingPlanDetails.getFeatures());
            pricingPlan.setIsActive(pricingPlanDetails.getIsActive());
            pricingPlan.setHighlight(pricingPlanDetails.getHighlight());
            PricingPlan saved = pricingPlanRepository.save(pricingPlan);
            pricingPlanSearchService.index(saved);
            return saved;
        }
        return null;
    }

    @CacheEvict(value = {"pricingPlans", "pricingPlans-active", "pricingPlans-all"}, allEntries = true)
    public void delete(Long id) {
        pricingPlanRepository.deleteById(id);
        pricingPlanSearchService.deleteFromIndex(id);
    }
    public long countAll()                    { return pricingPlanRepository.count(); }
    public long countByIsActive(Boolean v)    { return pricingPlanRepository.countByIsActive(v); }
    public long countByHighlight(Boolean v)   { return pricingPlanRepository.countByHighlight(v); }
    public List<Object[]> countByName()       { return pricingPlanRepository.countByName(); }
    public Double findAverageMonthlyPrice()   { return pricingPlanRepository.findAverageMonthlyPrice(); }
    public Double findMinMonthlyPrice()       { return pricingPlanRepository.findMinMonthlyPrice(); }
    public Double findMaxMonthlyPrice()       { return pricingPlanRepository.findMaxMonthlyPrice(); }
    public Double findAverageYearlyPrice()    { return pricingPlanRepository.findAverageYearlyPrice(); }


}
