package tn.esprit.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import tn.esprit.user.entity.Role;
import tn.esprit.user.entity.Users;

/**
 * Corps JSON d’inscription (incl. {@code recaptchaToken}).
 * Le rôle est une chaîne (ex. {@code ETUDIANT}) pour éviter les 400 JSON si l’énum Jackson ne matche pas.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegisterRequestDto {

    private String name;
    private String prenom;
    private String email;
    private String password;
    /** Valeurs : ADMIN, ENTREPRISE, ETUDIANT, TUTEUR (insensible à la casse). */
    private String role;
    private String adresse;
    private String num_tel;
    private String profession;
    private String specialte;
    private String recaptchaToken;

    public Users toUserEntity(Role resolvedRole) {
        Users u = new Users();
        u.setName(name);
        u.setPrenom(prenom);
        u.setEmail(email);
        u.setPassword(password);
        u.setRole(resolvedRole);
        if (adresse != null) {
            u.setAdresse(adresse);
        }
        if (num_tel != null) {
            u.setNum_tel(num_tel);
        }
        if (profession != null) {
            u.setProfession(profession);
        }
        if (specialte != null) {
            u.setSpecialte(specialte);
        }
        return u;
    }
}
