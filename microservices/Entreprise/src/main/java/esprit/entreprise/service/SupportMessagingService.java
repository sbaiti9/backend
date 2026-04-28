package esprit.entreprise.service;

import esprit.entreprise.DTO.SupportMessagePostRequest;
import esprit.entreprise.entity.SupportMessage;
import esprit.entreprise.repository.SupportMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SupportMessagingService {

    private static final int MAX_BODY = 4000;

    private final SupportMessageRepository supportMessageRepository;
    private final CompanyService companyService;

    public SupportMessagingService(
            SupportMessageRepository supportMessageRepository,
            CompanyService companyService
    ) {
        this.supportMessageRepository = supportMessageRepository;
        this.companyService = companyService;
    }

    public List<SupportMessage> listMessages(Long companyId) {
        if (companyId == null) {
            return List.of();
        }
        return supportMessageRepository.findByCompanyIdOrderByCreatedAtAsc(companyId);
    }

    @Transactional
    public SupportMessage post(SupportMessagePostRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Requête vide");
        }
        Long companyId = req.getCompanyId();
        if (companyId == null) {
            throw new IllegalArgumentException("companyId est requis");
        }
        if (companyService.findById(companyId).isEmpty()) {
            throw new IllegalArgumentException("Entreprise introuvable");
        }
        Long senderUserId = req.getSenderUserId();
        if (senderUserId == null) {
            throw new IllegalArgumentException("senderUserId est requis");
        }
        String role = normalizeRole(req.getSenderRole());
        if (role == null) {
            throw new IllegalArgumentException("senderRole doit être ADMIN ou ENTREPRISE");
        }
        String body = req.getBody() == null ? "" : req.getBody().trim();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("Le message ne peut pas être vide");
        }
        if (body.length() > MAX_BODY) {
            throw new IllegalArgumentException("Message trop long (max " + MAX_BODY + " caractères)");
        }

        SupportMessage m = new SupportMessage();
        m.setCompanyId(companyId);
        m.setSenderRole(role);
        m.setSenderUserId(senderUserId);
        m.setBody(body);
        m.setReadByAdmin("ADMIN".equals(role));
        return supportMessageRepository.save(m);
    }

    public long countUnreadFromEnterpriseForAdmin() {
        return supportMessageRepository.countUnreadFromEnterpriseForAdmin();
    }

    @Transactional
    public int markReadByAdminForCompany(Long companyId) {
        if (companyId == null) {
            return 0;
        }
        return supportMessageRepository.markReadByAdminForCompany(companyId);
    }

    private static String normalizeRole(String raw) {
        if (raw == null) return null;
        String r = raw.trim().toUpperCase();
        if ("ADMIN".equals(r) || "ENTREPRISE".equals(r)) {
            return r;
        }
        return null;
    }
}
