package esprit.entreprise.controller;

import esprit.entreprise.DTO.CompanyDTO;
import esprit.entreprise.DTO.JobOfferDTO;
import esprit.entreprise.entity.Company;
import esprit.entreprise.entity.JobOffer;
import esprit.entreprise.service.CompanyService;
import esprit.entreprise.service.JobOfferService;
import esprit.entreprise.service.JobOfferSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/entreprise")
public class EntrepriseRESTApi {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private JobOfferService jobOfferService;

    @Autowired
    private JobOfferSearchService jobOfferSearchService;

    @GetMapping("/hello")
    public String sayHello() {
        return "Hello World from entreprise microservice";
    }

    @GetMapping("/job-offers/search")
    public ResponseEntity<List<JobOffer>> searchJobOffers(@RequestParam("q") String q) {
        return ResponseEntity.ok(jobOfferSearchService.search(q));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  COMPANY ENDPOINTS
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * GET /entreprise/companies
     * Get all companies
     */
    @GetMapping("/companies")
    public ResponseEntity<List<Company>> getAllCompanies() {
        List<Company> companies = companyService.findAll();
        return ResponseEntity.ok(companies);
    }

    /**
     * GET /entreprise/companies/{id}
     * Get a specific company by ID
     */
    @GetMapping("/companies/{id}")
    public ResponseEntity<Company> getCompanyById(@PathVariable Long id) {
        return companyService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /entreprise/companies-by-user/{userId}
     * ⭐ NEW: Get company by userId (different route to avoid conflict)
     * This endpoint helps users find their own company profile
     */
    @GetMapping("/companies-by-user/{userId}")
    public ResponseEntity<Company> getCompanyByUserId(@PathVariable Long userId) {
        return companyService.findByUserId(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /entreprise/companies
     * Create a new company
     * ⭐ IMPORTANT: userId must be provided (link to Users from User microservice)
     */
    @PostMapping("/companies")
    public ResponseEntity<?> createCompany(@RequestBody CompanyDTO companyDTO) {
        try {
            // Validate required fields
            if (companyDTO.getUserId() == null) {
                return ResponseEntity.badRequest()
                        .body("userId is required");
            }

            // Check if company already exists for this user
            if (companyService.findByUserId(companyDTO.getUserId()).isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("A company already exists for this user");
            }

            // Check if email is already used
            if (companyDTO.getEmail() != null &&
                    companyService.findByEmail(companyDTO.getEmail()).isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Email already exists");
            }

            // Create new company
            Company company = new Company();
            company.setName(companyDTO.getName());
            company.setIndustry(companyDTO.getIndustry());
            company.setLocation(companyDTO.getLocation());
            company.setWebsite(companyDTO.getWebsite());
            company.setLogoUrl(companyDTO.getLogoUrl());
            company.setEmail(companyDTO.getEmail());
            company.setPhone(companyDTO.getPhone());
            company.setUserId(companyDTO.getUserId());

            Company savedCompany = companyService.save(company);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedCompany);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }

    /**
     * PUT /entreprise/companies/{id}
     * Update an existing company
     */
    @PutMapping("/companies/{id}")
    public ResponseEntity<?> updateCompany(@PathVariable Long id, @RequestBody CompanyDTO companyDTO) {
        try {
            Company updated = new Company();
            updated.setName(companyDTO.getName());
            updated.setIndustry(companyDTO.getIndustry());
            updated.setLocation(companyDTO.getLocation());
            updated.setWebsite(companyDTO.getWebsite());
            updated.setLogoUrl(companyDTO.getLogoUrl());
            updated.setEmail(companyDTO.getEmail());
            updated.setPhone(companyDTO.getPhone());

            Company result = companyService.updateCompany(id, updated);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }

    /**
     * DELETE /entreprise/companies/{id}
     * Delete a company
     */
    @DeleteMapping("/companies/{id}")
    public ResponseEntity<?> deleteCompany(@PathVariable Long id) {
        try {
            companyService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────
    //  COMPANY STATISTICS
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * GET /entreprise/companies/stats
     * Returns global statistics about companies
     *
     * Example response:
     * {
     *   "totalCompanies": 42,
     *   "byIndustry":  { "Tech": 18, "Finance": 10, ... },
     *   "byLocation":  { "Paris": 15, "Lyon": 8, ... },
     *   "topCompaniesByJobOffers": { "Google": 12, "Amazon": 9, ... },
     *   "totalJobOffers": 87
     * }
     */
    @GetMapping("/companies/stats")
    public ResponseEntity<Map<String, Object>> getCompanyStats() {
        Map<String, Object> stats = new HashMap<>();

        // 1. Total companies count
        stats.put("totalCompanies", companyService.countAll());

        // 2. Distribution by industry
        List<Object[]> byIndustry = companyService.countByIndustry();
        Map<String, Long> industryMap = new HashMap<>();
        for (Object[] row : byIndustry) {
            industryMap.put((String) row[0], (Long) row[1]);
        }
        stats.put("byIndustry", industryMap);

        // 3. Distribution by location (top 5)
        List<Object[]> byLocation = companyService.countByLocation();
        Map<String, Long> locationMap = new HashMap<>();
        byLocation.stream().limit(5)
                .forEach(row -> locationMap.put((String) row[0], (Long) row[1]));
        stats.put("byLocation", locationMap);

        // 4. Top 5 companies with most job offers
        List<Object[]> topByOffers = jobOfferService.countByCompany();
        Map<String, Long> topCompanies = new HashMap<>();
        topByOffers.stream().limit(5)
                .forEach(row -> topCompanies.put((String) row[0], (Long) row[1]));
        stats.put("topCompaniesByJobOffers", topCompanies);

        // 5. Total job offers across all companies
        stats.put("totalJobOffers", jobOfferService.countAll());

        return ResponseEntity.ok(stats);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  JOB OFFER ENDPOINTS
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * GET /entreprise/job-offers
     * Get all job offers with optional filters
     *
     * Query parameters:
     *   - contractType: CDI, CDD, INTERNSHIP, FREELANCE
     *   - location: city name
     *   - remote: ON_SITE, HYBRID, FULL_REMOTE
     */
    @GetMapping("/job-offers")
    public ResponseEntity<List<JobOffer>> getAllJobOffers(
            @RequestParam(required = false) String contractType,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String remote) {
        return ResponseEntity.ok(jobOfferService.findAll(contractType, location, remote));
    }

    /**
     * GET /entreprise/job-offers/{id}
     * Get a specific job offer by ID
     */
    @GetMapping("/job-offers/{id}")
    public ResponseEntity<JobOffer> getJobOfferById(@PathVariable Long id) {
        return jobOfferService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /entreprise/job-offers/company/{companyId}
     * Get all job offers for a specific company
     */
    @GetMapping("/job-offers/company/{companyId}")
    public ResponseEntity<List<JobOffer>> getJobOffersByCompany(@PathVariable Long companyId) {
        return ResponseEntity.ok(jobOfferService.findByCompanyId(companyId));
    }

    /**
     * POST /entreprise/job-offers
     * Create a new job offer
     */
    @PostMapping("/job-offers")
    public ResponseEntity<?> createJobOffer(@RequestBody JobOfferDTO jobOfferDTO) {
        try {
            if (jobOfferDTO.getCompanyId() == null) {
                return ResponseEntity.badRequest()
                        .body("companyId is required");
            }

            Company company = companyService.findById(jobOfferDTO.getCompanyId())
                    .orElseThrow(() -> new RuntimeException("Company not found with ID: " + jobOfferDTO.getCompanyId()));

            JobOffer jobOffer = new JobOffer();
            jobOffer.setTitle(jobOfferDTO.getTitle());
            jobOffer.setDescription(jobOfferDTO.getDescription());
            jobOffer.setContractType(jobOfferDTO.getContractType());
            jobOffer.setLocation(jobOfferDTO.getLocation());
            jobOffer.setSalary(jobOfferDTO.getSalary());
            jobOffer.setRemote(jobOfferDTO.getRemote());
            jobOffer.setRequirements(jobOfferDTO.getRequirements());
            jobOffer.setIsActive(jobOfferDTO.getIsActive() != null ? jobOfferDTO.getIsActive() : true);
            jobOffer.setCompany(company);

            JobOffer savedJobOffer = jobOfferService.save(jobOffer);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedJobOffer);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }

    /**
     * PUT /entreprise/job-offers/{id}
     * Update an existing job offer
     */
    @PutMapping("/job-offers/{id}")
    public ResponseEntity<?> updateJobOffer(@PathVariable Long id, @RequestBody JobOfferDTO jobOfferDTO) {
        try {
            JobOffer updated = new JobOffer();
            updated.setTitle(jobOfferDTO.getTitle());
            updated.setDescription(jobOfferDTO.getDescription());
            updated.setContractType(jobOfferDTO.getContractType());
            updated.setLocation(jobOfferDTO.getLocation());
            updated.setSalary(jobOfferDTO.getSalary());
            updated.setRemote(jobOfferDTO.getRemote());
            updated.setRequirements(jobOfferDTO.getRequirements());
            updated.setIsActive(jobOfferDTO.getIsActive());

            JobOffer result = jobOfferService.updateJobOffer(id, updated);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }

    /**
     * DELETE /entreprise/job-offers/{id}
     * Delete a job offer
     */
    @DeleteMapping("/job-offers/{id}")
    public ResponseEntity<?> deleteJobOffer(@PathVariable Long id) {
        try {
            jobOfferService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException | EmptyResultDataAccessException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────
    //  JOB OFFER STATISTICS
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * GET /entreprise/job-offers/stats
     * Returns global statistics about job offers
     *
     * Example response:
     * {
     *   "totalOffers":    87,
     *   "activeOffers":   72,
     *   "inactiveOffers": 15,
     *   "byContractType": { "CDI": 40, "CDD": 20, "INTERNSHIP": 18, "FREELANCE": 9 },
     *   "byRemote":       { "ON_SITE": 50, "HYBRID": 25, "FULL_REMOTE": 12 },
     *   "byLocation":     { "Paris": 30, "Lyon": 20, ... },
     *   "averageSalary":  45000,
     *   "minSalary":      18000,
     *   "maxSalary":      95000
     * }
     */
    @GetMapping("/job-offers/stats")
    public ResponseEntity<Map<String, Object>> getJobOfferStats() {
        Map<String, Object> stats = new HashMap<>();

        // 1. Active / Inactive counts
        stats.put("totalOffers",    jobOfferService.countAll());
        stats.put("activeOffers",   jobOfferService.countByIsActive(true));
        stats.put("inactiveOffers", jobOfferService.countByIsActive(false));

        // 2. Distribution by contract type
        List<Object[]> byContract = jobOfferService.countByContractType();
        Map<String, Long> contractMap = new HashMap<>();
        for (Object[] row : byContract) {
            contractMap.put((String) row[0], (Long) row[1]);
        }
        stats.put("byContractType", contractMap);

        // 3. Distribution by remote type
        List<Object[]> byRemote = jobOfferService.countByRemote();
        Map<String, Long> remoteMap = new HashMap<>();
        for (Object[] row : byRemote) {
            remoteMap.put((String) row[0], (Long) row[1]);
        }
        stats.put("byRemote", remoteMap);

        // 4. Distribution by location (top 5)
        List<Object[]> byLocation = jobOfferService.countByLocation();
        Map<String, Long> locationMap = new HashMap<>();
        byLocation.stream().limit(5)
                .forEach(row -> locationMap.put((String) row[0], (Long) row[1]));
        stats.put("byLocation", locationMap);

        // 5. Salary statistics
        Double avg = jobOfferService.findAverageSalary();
        Double min = jobOfferService.findMinSalary();
        Double max = jobOfferService.findMaxSalary();
        stats.put("averageSalary", avg != null ? Math.round(avg) : 0);
        stats.put("minSalary",     min != null ? min : 0);
        stats.put("maxSalary",     max != null ? max : 0);

        return ResponseEntity.ok(stats);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  GLOBAL STATISTICS
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * GET /entreprise/stats
     * Returns a comprehensive overview combining Company + JobOffer statistics
     * Perfect for admin dashboard
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getGlobalStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCompanies",   companyService.countAll());
        stats.put("totalJobOffers",   jobOfferService.countAll());
        stats.put("activeJobOffers",  jobOfferService.countByIsActive(true));
        stats.put("topIndustries",    buildMap(companyService.countByIndustry(), 3));
        stats.put("topContractTypes", buildMap(jobOfferService.countByContractType(), 4));
        return ResponseEntity.ok(stats);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    //  HELPER METHODS
    // ──────────────────────────────────────────────────────────────────────────────

    private Map<String, Long> buildMap(List<Object[]> rows, int limit) {
        Map<String, Long> map = new HashMap<>();
        rows.stream().limit(limit).forEach(r -> map.put((String) r[0], (Long) r[1]));
        return map;
    }

    // ──────────────────────────────────────────────────────────────────────────────
    //  EXCEPTION HANDLERS
    // ──────────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("An unexpected error occurred: " + e.getMessage());
    }
}