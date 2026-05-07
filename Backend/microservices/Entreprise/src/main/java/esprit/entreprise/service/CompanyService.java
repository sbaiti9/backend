package esprit.entreprise.service;

import esprit.entreprise.entity.Company;
import esprit.entreprise.repository.CompanyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CompanyService {

    @Autowired
    private CompanyRepository companyRepository;

    @Cacheable("companies")
    public List<Company> findAll() {
        return companyRepository.findAll();
    }

    public Optional<Company> findById(Long id) {
        return companyRepository.findById(id);
    }

    public Optional<Company> findByUserId(Long userId) {
        return companyRepository.findByUserId(userId);
    }

    public Optional<Company> findByEmail(String email) {
        return companyRepository.findByEmail(email);
    }

    @Transactional
    @CacheEvict(value = "companies", allEntries = true)
    public Company save(Company company) {
        if (company.getUserId() == null) {
            throw new RuntimeException("userId is required to create a company");
        }
        return companyRepository.save(company);
    }

    @Transactional
    @CacheEvict(value = "companies", allEntries = true)
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
        return companyRepository.save(existing);
    }

    @Transactional
    @CacheEvict(value = "companies", allEntries = true)
    public void delete(Long id) {
        companyRepository.deleteById(id);
    }

    public long countAll() { return companyRepository.count(); }
    public List<Object[]> countByIndustry() { return companyRepository.countByIndustry(); }
    public List<Object[]> countByLocation() { return companyRepository.countByLocation(); }
}
