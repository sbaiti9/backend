package tn.esprit.user.service;

import org.springframework.stereotype.Service;
import tn.esprit.user.entity.GeoAuthEvent;
import tn.esprit.user.repository.GeoAuthEventRepository;

import java.time.LocalDateTime;

/**
 * Lightweight "AI-style" risk gate for admin unlock decisions.
 * We reuse geo-security anomaly signals (impossible travel) to detect potential account takeover.
 */
@Service
public class AuthRiskAssessmentService {

    private final GeoAuthEventRepository geoAuthEventRepository;

    public AuthRiskAssessmentService(GeoAuthEventRepository geoAuthEventRepository) {
        this.geoAuthEventRepository = geoAuthEventRepository;
    }

    public RiskDecision assessUnlockRisk(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        GeoAuthEvent anomaly = geoAuthEventRepository
                .findTopByUserIdAndImpossibleTravelIsTrueOrderByCreatedAtDesc(userId)
                .orElse(null);

        if (anomaly != null && anomaly.getCreatedAt() != null && anomaly.getCreatedAt().isAfter(since)) {
            StringBuilder fr = new StringBuilder();
            fr.append("Anomalie de sécurité géographique détectée dans les dernières 24 h : ");
            fr.append("« déplacement impossible » entre deux connexions (risque de compte compromis ou activité suspecte).");
            fr.append(" Dernière alerte : ").append(anomaly.getCreatedAt()).append(".");
            if (anomaly.getCity() != null || anomaly.getCountry() != null) {
                fr.append(" Localisation signalée : ");
                boolean hasLoc = false;
                if (anomaly.getCity() != null && !anomaly.getCity().isBlank()) {
                    fr.append(anomaly.getCity().trim());
                    hasLoc = true;
                }
                if (anomaly.getCountry() != null && !anomaly.getCountry().isBlank()) {
                    if (hasLoc) {
                        fr.append(", ");
                    }
                    fr.append(anomaly.getCountry().trim());
                }
                fr.append(".");
            }
            return new RiskDecision(false, fr.toString());
        }
        return new RiskDecision(true, "Aucune anomalie géo-sécurité récente détectée sur ce compte.");
    }

    public record RiskDecision(boolean allowUnlock, String reason) {}
}

