package tn.esprit.user.controller;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import tn.esprit.user.entity.PlatformEngagementTier;
import tn.esprit.user.entity.Role;
import tn.esprit.user.entity.Status;
import tn.esprit.user.entity.Users;
import tn.esprit.user.repository.UserRepository;
import tn.esprit.user.dto.LoginGeoSnapshot;
import tn.esprit.user.dto.RegisterRequestDto;
import tn.esprit.user.entity.GeoAuthEvent;
import tn.esprit.user.entity.GeoEventType;
import tn.esprit.user.security.JwtService;
import tn.esprit.user.service.AdminSecurityAlertService;
import tn.esprit.user.service.GeoSecurityService;
import tn.esprit.user.service.LoginLocationAiService;
import tn.esprit.user.service.PlatformEngagementService;
import tn.esprit.user.service.RecaptchaVerificationService;
import tn.esprit.user.service.SearchService;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("user")
@RequiredArgsConstructor

public class UserAuthController {

    private final UserRepository repo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final GeoSecurityService geoSecurityService;
    private final AdminSecurityAlertService adminSecurityAlertService;
    private final PlatformEngagementService platformEngagementService;
    private final LoginLocationAiService loginLocationAiService;
    private final RecaptchaVerificationService recaptchaVerificationService;
    private final SearchService searchService;

    // defaults: 3 attempts, 30 minutes lock
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int LOCK_MINUTES = 30;

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequestDto dto) {

        if (!recaptchaVerificationService.verifyOrDisabled(dto.getRecaptchaToken())) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Vérification anti-robot invalide ou expirée. Cochez à nouveau « Je ne suis pas un robot ».");
            response.put("code", "RECAPTCHA_FAILED");
            return ResponseEntity.badRequest().body(response);
        }

        Role parsedRole;
        try {
            if (dto.getRole() == null || dto.getRole().isBlank()) {
                Map<String, String> response = new HashMap<>();
                response.put("error", "Rôle requis");
                response.put("code", "ROLE_REQUIRED");
                return ResponseEntity.badRequest().body(response);
            }
            parsedRole = Role.valueOf(dto.getRole().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Rôle invalide : " + dto.getRole());
            response.put("code", "ROLE_INVALID");
            return ResponseEntity.badRequest().body(response);
        }

        Users user = dto.toUserEntity(parsedRole);

        // ✅ Vérifier que l'email n'existe pas
        if (repo.findByEmail(user.getEmail()).isPresent()) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Email déjà utilisé !");
            return ResponseEntity.badRequest().body(response);
        }

        // ✅ Initialiser les champs requis s'ils sont null
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Mot de passe requis !");
            return ResponseEntity.badRequest().body(response);
        }

        if (user.getRole() == null) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Rôle requis !");
            return ResponseEntity.badRequest().body(response);
        }

        // ✅ Initialiser le statut par défaut
        if (user.getStatus() == null) {
            user.setStatus(Status.ACTIVE);
        }
        if (user.getTokenVersion() == null) {
            user.setTokenVersion(0);
        }

        // ✅ Encoder le mot de passe
        user.setPassword(encoder.encode(user.getPassword()));

        // ✅ Sauvegarder et récupérer l'ID
        Users savedUser = repo.save(user);
        searchService.indexUser(savedUser);

        // ✅ Retourner un objet JSON avec l'ID
        Map<String, Object> response = new HashMap<>();
        response.put("id", savedUser.getId());
        response.put("email", savedUser.getEmail());
        response.put("name", savedUser.getName());
        response.put("role", savedUser.getRole());
        response.put("message", "Utilisateur enregistré avec succès !");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {

        var userOpt = repo.findByEmail(request.getEmail());

        if (userOpt.isEmpty())
            return ResponseEntity.status(401).body("Email ou mot de passe incorrect");

        Users user = userOpt.get();

        // Temporary lock (anti brute-force)
        if (user.getLockUntil() != null) {
            if (user.getLockUntil().isAfter(LocalDateTime.now())) {
                Map<String, Object> err = new HashMap<>();
                err.put("error", "Compte temporairement bloqué (30 minutes après 3 mots de passe incorrects).");
                err.put("lockedUntil", user.getLockUntil().toString());
                err.put(
                        "hint",
                        "À la fin de ce délai, vous pourrez vous reconnecter avec le bon mot de passe. "
                                + "Un administrateur peut tenter un déblocage anticipé : si l’analyse de risque refuse, "
                                + "le compte reste bloqué ; si elle autorise, le déblocage est appliqué.");
                return ResponseEntity.status(423).body(err);
            } else {
                // Lock expired: reset counters
                user.setLockUntil(null);
                user.setFailedLoginAttempts(0);
                repo.save(user);
            }
        }

        if (user.getStatus() != Status.ACTIVE) {
            Map<String, String> err = new HashMap<>();
            if (user.getStatus() == Status.FROZEN) {
                err.put("error", "Compte gelé — contactez le support.");
            } else {
                err.put("error", "Compte inactif — accès refusé.");
            }
            return ResponseEntity.status(403).body(err);
        }

        if (!encoder.matches(request.getPassword(), user.getPassword())) {
            int next = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(next);

            if (next >= MAX_FAILED_ATTEMPTS) {
                LocalDateTime until = LocalDateTime.now().plusMinutes(LOCK_MINUTES);
                user.setLockUntil(until);
                repo.save(user);
                try {
                    adminSecurityAlertService.notifyUserTemporarilyBlocked(user.getId(), next, until);
                } catch (Exception ignored) {
                }
                Map<String, Object> err = new HashMap<>();
                err.put("error", "Compte bloqué 30 minutes après 3 tentatives de mot de passe incorrectes.");
                err.put("lockedUntil", until.toString());
                err.put(
                        "hint",
                        "Après 30 minutes, vous pourrez réessayer avec le bon mot de passe. "
                                + "Un administrateur peut proposer un déblocage anticipé : si l’analyse de risque refuse, "
                                + "le compte n’est pas débloqué ; si elle autorise, le déblocage est effectué.");
                return ResponseEntity.status(423).body(err);
            }

            repo.save(user);
            return ResponseEntity.status(401).body("Email ou mot de passe incorrect");
        }

        // Successful login: reset counters
        if (user.getFailedLoginAttempts() != 0 || user.getLockUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockUntil(null);
            repo.save(user);
        }

        String token = jwtService.generateToken(user);

        PlatformEngagementService.EngagementSnapshot engagement;
        try {
            engagement = platformEngagementService.processLoginEngagement(user.getId());
        } catch (Exception ex) {
            log.warn("Engagement points skipped: {}", ex.getMessage());
            engagement = new PlatformEngagementService.EngagementSnapshot(
                    0, 0, 0, PlatformEngagementTier.BRONZE, "");
        }

        GeoAuthEvent loginGeo = null;
        try {
            loginGeo = geoSecurityService.recordEvent(user.getId(), GeoEventType.LOGIN, httpRequest);
        } catch (Exception ignored) {
        }
        LoginGeoSnapshot loginLoc = loginLocationAiService.describe(loginGeo);

        return ResponseEntity.ok(new JwtResponse(
                token,
                user.getRole(),
                user.getId(),
                engagement.minutesEstimated(),
                engagement.pointsEarnedThisLogin(),
                engagement.totalPoints(),
                engagement.tier().name(),
                engagement.tier().getLabelFr(),
                engagement.aiMessageFr() == null ? "" : engagement.aiMessageFr(),
                loginLoc.city(),
                loginLoc.country(),
                loginLoc.latitude(),
                loginLoc.longitude(),
                loginLoc.aiMessageFr()));
    }

    @Getter @Setter
    static class LoginRequest {
        private String email;
        private String password;
    }

    @Getter
    @AllArgsConstructor
    static class JwtResponse {
        private String token;
        private Object role;
        /** Identifiant compte User — nécessaire pour lier l’entreprise (companies-by-user). */
        private Long userId;
        /** Minutes estimées depuis le dernier LOGIN (plafonnées). */
        private int engagementMinutesEstimated;
        /** Points gagnés sur cette connexion. */
        private int engagementPointsEarned;
        /** Total cumulé points plateforme Skillio. */
        private int engagementPointsTotal;
        /** Palier engagement : BRONZE, SILVER ou GOLD (seuils sur points totaux). */
        private String engagementTier;
        /** Libellé français du palier (Bronze, Argent, Or). */
        private String engagementTierLabelFr;
        /** Message IA (Groq) ou texte serveur. */
        private String engagementAiMessageFr;
        /** Ville approximative (géo-IP) au moment du login. */
        private String loginGeoCity;
        /** Pays approximatif (géo-IP). */
        private String loginGeoCountry;
        private Double loginGeoLatitude;
        private Double loginGeoLongitude;
        /** Phrase d’accueil (Groq) liée à la zone de connexion ; vide si IA désactivée. */
        private String loginLocationAiMessageFr;
    }
}