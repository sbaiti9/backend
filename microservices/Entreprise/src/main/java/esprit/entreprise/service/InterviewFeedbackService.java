package esprit.entreprise.service;

import esprit.entreprise.DTO.InterviewFeedbackSubmitRequest;
import esprit.entreprise.entity.Company;
import esprit.entreprise.entity.Interview;
import esprit.entreprise.entity.InterviewCandidateFeedback;
import esprit.entreprise.entity.JobOffer;
import esprit.entreprise.repository.CompanyRepository;
import esprit.entreprise.repository.InterviewCandidateFeedbackRepository;
import esprit.entreprise.repository.InterviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InterviewFeedbackService {

    public static final int ANONYMITY_MIN_RESPONSES = 5;

    private final InterviewRepository interviewRepository;
    private final InterviewCandidateFeedbackRepository feedbackRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public Map<String, Object> submit(InterviewFeedbackSubmitRequest req) {
        if (req == null || req.getInterviewId() == null || req.getCandidateUserId() == null) {
            throw new IllegalArgumentException("interviewId et candidateUserId sont requis.");
        }
        if (req.getOverallRating() == null) {
            throw new IllegalArgumentException("La note globale (1–5) est obligatoire.");
        }

        Interview interview = interviewRepository.findById(req.getInterviewId())
                .orElseThrow(() -> new IllegalArgumentException("Entretien introuvable."));

        if (!interview.getCandidateUserId().equals(req.getCandidateUserId())) {
            throw new IllegalArgumentException("Ce compte ne peut pas soumettre d'avis pour cet entretien.");
        }

        Interview.InterviewStatus st = interview.getStatus();
        if (st != Interview.InterviewStatus.COMPLETED && st != Interview.InterviewStatus.NO_SHOW) {
            throw new IllegalArgumentException(
                    "Avis possible uniquement après un entretien terminé ou marqué « absent ».");
        }

        if (feedbackRepository.existsByInterviewId(interview.getId())) {
            throw new IllegalArgumentException("Un avis a déjà été enregistré pour cet entretien.");
        }

        JobOffer offer = interview.getJobOffer();
        if (offer == null || offer.getCompany() == null || offer.getCompany().getId() == null) {
            throw new IllegalStateException("Offre ou entreprise invalide pour cet entretien.");
        }

        int overall = clamp(req.getOverallRating(), 1, 5);
        InterviewCandidateFeedback row = InterviewCandidateFeedback.builder()
                .interviewId(interview.getId())
                .jobOfferId(offer.getId())
                .companyId(offer.getCompany().getId())
                .candidateUserId(interview.getCandidateUserId())
                .overallRating(overall)
                .professionalismRating(clampOptionalRating(req.getProfessionalismRating()))
                .punctualityRating(clampOptionalRating(req.getPunctualityRating()))
                .clarityRating(clampOptionalRating(req.getClarityRating()))
                .comment(sanitizeComment(req.getComment()))
                .build();

        InterviewCandidateFeedback saved = feedbackRepository.save(row);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "Merci : ton avis anonyme a été enregistré. L'entreprise ne voit que des statistiques agrégées.");
        out.put("id", saved.getId());
        return out;
    }

    public Map<String, Object> statsForCompany(Long companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw new IllegalArgumentException("Entreprise introuvable.");
        }
        List<Object[]> rows = feedbackRepository.aggregateForCompany(companyId);
        Object[] row = rows.isEmpty() ? null : rows.get(0);
        long count = 0L;
        Double avgOverall = null;
        Double avgPro = null;
        Double avgPunc = null;
        Double avgClar = null;
        if (row != null && row[0] != null) {
            count = ((Number) row[0]).longValue();
            avgOverall = row[1] != null ? ((Number) row[1]).doubleValue() : null;
            avgPro = row[2] != null ? ((Number) row[2]).doubleValue() : null;
            avgPunc = row[3] != null ? ((Number) row[3]).doubleValue() : null;
            avgClar = row[4] != null ? ((Number) row[4]).doubleValue() : null;
        }

        boolean thresholdMet = count >= ANONYMITY_MIN_RESPONSES;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("companyId", companyId);
        out.put("responseCount", count);
        out.put("thresholdMet", thresholdMet);
        out.put("minResponsesForDetail", ANONYMITY_MIN_RESPONSES);
        if (thresholdMet) {
            out.put("avgOverall", round2(avgOverall));
            out.put("avgProfessionalism", round2(avgPro));
            out.put("avgPunctuality", round2(avgPunc));
            out.put("avgClarity", round2(avgClar));
        } else {
            out.put("avgOverall", null);
            out.put("avgProfessionalism", null);
            out.put("avgPunctuality", null);
            out.put("avgClarity", null);
            out.put("hint", "Les moyennes détaillées s'affichent à partir de " + ANONYMITY_MIN_RESPONSES
                    + " retours anonymes pour protéger les candidats.");
        }
        return out;
    }

    public Map<String, Object> adminStats() {
        long total = feedbackRepository.countTotal();
        Double globalAvg = feedbackRepository.avgOverallGlobal();
        List<Object[]> raw = feedbackRepository.aggregateByCompanyForAdmin();
        List<Map<String, Object>> byCompany = new ArrayList<>();
        for (Object[] r : raw) {
            Long cid = (Long) r[0];
            long cnt = ((Number) r[1]).longValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("companyId", cid);
            m.put("companyName", companyRepository.findById(cid).map(Company::getName).orElse("(inconnu)"));
            m.put("responseCount", cnt);
            m.put("avgOverall", r[2] != null ? round2(((Number) r[2]).doubleValue()) : null);
            m.put("avgProfessionalism", r[3] != null ? round2(((Number) r[3]).doubleValue()) : null);
            m.put("avgPunctuality", r[4] != null ? round2(((Number) r[4]).doubleValue()) : null);
            m.put("avgClarity", r[5] != null ? round2(((Number) r[5]).doubleValue()) : null);
            byCompany.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalFeedbacks", total);
        out.put("avgOverallGlobal", globalAvg != null ? round2(globalAvg) : null);
        out.put("byCompany", byCompany);
        return out;
    }

    private static Double round2(Double v) {
        if (v == null || v.isNaN()) {
            return null;
        }
        return Math.round(v * 100.0) / 100.0;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static Integer clampOptionalRating(Integer v) {
        if (v == null) {
            return null;
        }
        return clamp(v, 1, 5);
    }

    private static String sanitizeComment(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) {
            return null;
        }
        if (t.length() > 400) {
            return t.substring(0, 400);
        }
        return t;
    }
}
