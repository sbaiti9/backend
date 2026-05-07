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

    @Cacheable("plans-active")
    public List<PricingPlan> findAllActive() {
        return pricingPlanRepository.findByIsActiveTrue();
    }

    @Cacheable("plans")
    public List<PricingPlan> findAll() {
        return pricingPlanRepository.findAll();
    }

    public Optional<PricingPlan> findById(Long id) {
        return pricingPlanRepository.findById(id);
    }

    @CacheEvict(value = {"plans", "plans-active"}, allEntries = true)
    public PricingPlan save(PricingPlan pricingPlan) {
        if (pricingPlan.getIsActive() == null) {
            pricingPlan.setIsActive(true);
        }
        return pricingPlanRepository.save(pricingPlan);
    }

    @CacheEvict(value = {"plans", "plans-active"}, allEntries = true)
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
            return pricingPlanRepository.save(pricingPlan);
        }
        return null;
    }

    @CacheEvict(value = {"plans", "plans-active"}, allEntries = true)
    public void delete(Long id) {
        pricingPlanRepository.deleteById(id);
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
