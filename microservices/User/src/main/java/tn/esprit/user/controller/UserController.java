package tn.esprit.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import tn.esprit.user.entity.Users;
import tn.esprit.user.entity.Role;
import tn.esprit.user.service.SearchService;
import tn.esprit.user.service.UsersCachedService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UsersCachedService usersService;
    private final PasswordEncoder encoder;
    private final SearchService searchService;

    // 🔹 ADMIN : voir tous les utilisateurs
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping
    public List<Users> getAllUsers() {
        return usersService.findAll();
    }

    // 🔹 Voir son profil
    @GetMapping("/users/me")
    public ResponseEntity<?> getMyProfile(Principal principal) {
        String email = principal.getName();
        Users user = usersService.findByEmail(email).orElse(null);

        if (user == null)
            return ResponseEntity.status(404).body("Utilisateur introuvable");

        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    // 🔹 Modifier son profil
    @PutMapping("/users/me")
    public ResponseEntity<?> updateMyProfile(Principal principal,
                                             @RequestBody Users updatedUser) {

        String email = principal.getName();
        Users user = usersService.findByEmail(email).orElse(null);

        if (user == null)
            return ResponseEntity.status(404).body("Utilisateur introuvable");

        user.setName(updatedUser.getName());
        user.setPrenom(updatedUser.getPrenom());
        user.setAdresse(updatedUser.getAdresse());
        user.setNum_tel(updatedUser.getNum_tel());
        user.setProfession(updatedUser.getProfession());
        user.setSpecialte(updatedUser.getSpecialte());

        usersService.saveAndIndex(user);

        return ResponseEntity.ok("Profil mis à jour !");
    }

    // 🔹 Supprimer un utilisateur (ADMIN)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {

        if (usersService.findById(id).isEmpty())
            return ResponseEntity.notFound().build();

        usersService.deleteByIdAndDeindex(id);
        return ResponseEntity.ok("Utilisateur supprimé !");
    }

    // ✅ NOUVEAU: Endpoint PUBLIC pour Feign Client
    // Utilisé par Entreprise MS pour récupérer les infos utilisateur
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        System.out.println("📡 UserController: Récupération utilisateur ID=" + id);

        Users user = usersService.findById(id).orElse(null);

        if (user == null) {
            System.out.println("❌ Utilisateur non trouvé: " + id);
            return ResponseEntity.status(404).body("Utilisateur non trouvé");
        }

        // ✅ Ne pas retourner le mot de passe
        user.setPassword(null);

        System.out.println("✅ Utilisateur retourné:");
        System.out.println("   ID: " + user.getId());
        System.out.println("   Name: " + user.getName());
        System.out.println("   Education: " + user.getEducationLevel());
        System.out.println("   Rating: " + user.getAverage_rating());
        System.out.println("   Status: " + user.getStatus());

        return ResponseEntity.ok(user);
    }

    // ✅ NOUVEAU: Endpoint PUBLIC pour profil détaillé
    @GetMapping("/users/{id}/profile")
    public ResponseEntity<?> getUserProfile(@PathVariable Long id) {
        Users user = usersService.findById(id).orElse(null);

        if (user == null)
            return ResponseEntity.status(404).body("Utilisateur non trouvé");

        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    // ✅ NOUVEAU: Endpoint PUBLIC pour infos basiques
    @GetMapping("/public/{id}")
    public ResponseEntity<?> getPublicUserProfile(@PathVariable Long id) {
        Users user = usersService.findById(id).orElse(null);

        if (user == null)
            return ResponseEntity.status(404).body("Utilisateur non trouvé");

        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/search")
    public List<Users> search(@RequestParam("q") String q) {
        return searchService.search(q);
    }
}