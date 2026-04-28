package esprit.entreprise.service;

import esprit.entreprise.entity.JobOffer;
import esprit.entreprise.repository.InterviewCandidateFeedbackRepository;
import esprit.entreprise.repository.InterviewRepository;
import esprit.entreprise.repository.JobApplicationRepository;
import esprit.entreprise.repository.JobOfferRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class JobOfferService {

    @Autowired
    private JobOfferRepository jobOfferRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private InterviewCandidateFeedbackRepository interviewCandidateFeedbackRepository;

    public List<JobOffer> findAll(String contractType, String location, String remote) {
        if (contractType == null && location == null && remote == null) {
            return jobOfferRepository.findAll();
        }
        return jobOfferRepository.findByFilters(contractType, location, remote);
    }

    public Optional<JobOffer> findById(Long id) {
        return jobOfferRepository.findById(id);
    }

    public List<JobOffer> findByCompanyId(Long companyId) {
        return jobOfferRepository.findByCompany_Id(companyId);
    }

    @Transactional
    public JobOffer save(JobOffer jobOffer) {
        return jobOfferRepository.save(jobOffer);
    }

    @Transactional
    public JobOffer updateJobOffer(Long id, JobOffer updatedData) {
        JobOffer existing = jobOfferRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Job Offer not found with id: " + id));
            
        existing.setTitle(updatedData.getTitle());
        existing.setDescription(updatedData.getDescription());
        existing.setContractType(updatedData.getContractType());
        existing.setLocation(updatedData.getLocation());
        existing.setSalary(updatedData.getSalary());
        existing.setRemote(updatedData.getRemote());
        existing.setRequirements(updatedData.getRequirements());
        existing.setIsActive(updatedData.getIsActive());
        
        // DO NOT update companyId or createdAt
        return jobOfferRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) {
            throw new NoSuchElementException("Job Offer not found with id: null");
        }
        if (!jobOfferRepository.existsById(id)) {
            throw new NoSuchElementException("Job Offer not found with id: " + id);
        }

        // Delete dependent rows first to avoid FK constraint failures.
        // Order matters: feedback may reference interviews/offers; interviews/applications reference offers.
        interviewCandidateFeedbackRepository.deleteByJobOfferId(id);
        interviewRepository.deleteAll(interviewRepository.findByJobOfferIdOrderByInterviewDateAsc(id));
        jobApplicationRepository.deleteAll(jobApplicationRepository.findByJobOfferId(id));

        jobOfferRepository.deleteById(id);
    }
    public long countAll() {
        return jobOfferRepository.count();
    }

    public long countByIsActive(Boolean isActive) {
        return jobOfferRepository.countByIsActive(isActive);
    }

    public List<Object[]> countByContractType() {
        return jobOfferRepository.countByContractType();
    }

    public List<Object[]> countByRemote() {
        return jobOfferRepository.countByRemote();
    }

    public List<Object[]> countByLocation() {
        return jobOfferRepository.countByLocation();
    }

    public List<Object[]> countByCompany() {
        return jobOfferRepository.countByCompany();
    }

    public Double findAverageSalary() {
        return jobOfferRepository.findAverageSalary();
    }

    public Double findMinSalary() {
        return jobOfferRepository.findMinSalary();
    }

    public Double findMaxSalary() {
        return jobOfferRepository.findMaxSalary();
    }
}
