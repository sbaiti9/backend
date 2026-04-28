package tn.esprit.user.dto;

/**
 * Localisation approximative au moment du login (géo-IP) + phrase d’accueil optionnelle (IA).
 */
public record LoginGeoSnapshot(
        String city,
        String country,
        Double latitude,
        Double longitude,
        String aiMessageFr
) {
    public static LoginGeoSnapshot empty() {
        return new LoginGeoSnapshot(null, null, null, null, "");
    }
}
