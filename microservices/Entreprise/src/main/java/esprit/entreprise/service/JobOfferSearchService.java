package esprit.entreprise.service;

import esprit.entreprise.entity.JobOffer;
import esprit.entreprise.repository.JobOfferSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobOfferSearchService {
    private final JobOfferSearchRepository searchRepo;

    public void index(JobOffer offer) {
        if (offer == null || offer.getId() == null) return;
        offer.setEsId(String.valueOf(offer.getId()));
        offer.setName(offer.getTitle());
        searchRepo.save(offer);
    }

    public List<JobOffer> search(String q) {
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

