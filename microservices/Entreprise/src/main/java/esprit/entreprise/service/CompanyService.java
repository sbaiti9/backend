package esprit.entreprise.service;

import esprit.entreprise.entity.Company;
import esprit.entreprise.repository.CompanyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CompanyService {

    @Autowired
    private CompanyRepository companyRepository;

    /**
     * Get all companies
     */
    public List<Company> findAll() {
        return companyRepository.findAll();
    }

    /**
     * Get company by ID
     */
    public Optional<Company> findById(Long id) {
        return companyRepository.findById(id);
    }

    /**
     * ⭐ NEW: Get company by userId (to find company managed by a user)
     */
    public Optional<Company> findByUserId(Long userId) {
        return companyRepository.findByUserId(userId);
    }

    /**
     * Get company by email
     */
    public Optional<Company> findByEmail(String email) {
        return companyRepository.findByEmail(email);
    }

    /**
     * Create a new company
     * ⭐ IMPORTANT: userId must be provided and must exist in Users microservice
     */
    @Transactional
    public Company save(Company company) {
        if (company.getUserId() == null) {
            throw new RuntimeException("userId is required to create a company");
        }
        return companyRepository.save(company);
    }

    /**
     * Update existing company
     * ⭐ IMPORTANT: userId cannot be updated (immutable)
     */
    @Transactional
    public Company updateCompany(Long id, Company updatedData) {
        Company existing = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found with id: " + id));

        existing.setName(updatedData.getName());
        existing.setIndustry(updatedData.getIndustry());
        existing.setLocation(updatedData.getLocation());
        existing.setWebsite(updatedData.getWebsite());
        existing.setLogoUrl(updatedData.getLogoUrl());
        existing.setEmail(updatedData.getEmail());
        existing.setPhone(updatedData.getPhone());

        // DO NOT update userId or createdAt - they are immutable
        return companyRepository.save(existing);
    }

    /**
     * Delete a company
     */
    @Transactional
    public void delete(Long id) {
        companyRepository.deleteById(id);
    }

    /**
     * Count total companies
     */
    public long countAll() {
        return companyRepository.count();
    }

    /**
     * Get statistics: count by industry
     */
    public List<Object[]> countByIndustry() {
        return companyRepository.countByIndustry();
    }

    /**
     * Get statistics: count by location
     */
    public List<Object[]> countByLocation() {
        return companyRepository.countByLocation();
    }
}