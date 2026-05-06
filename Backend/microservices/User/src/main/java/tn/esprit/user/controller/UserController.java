package tn.esprit.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import tn.esprit.user.entity.Users;
import tn.esprit.user.entity.Role;
import tn.esprit.user.repository.UserRepository;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository repo;
    private final PasswordEncoder encoder;

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping
    public List<Users> getAllUsers() {
        return repo.findAll();
    }

    @GetMapping("/users/me")
    public ResponseEntity<?> getMyProfile(Principal principal) {
        String email = principal.getName();
        Users user = repo.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.status(404).body("Utilisateur introuvable");
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/users/me")
    public ResponseEntity<?> updateMyProfile(Principal principal, @RequestBody Users updatedUser) {
        String email = principal.getName();
        Users user = repo.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.status(404).body("Utilisateur introuvable");
        user.setName(updatedUser.getName());
        user.setPrenom(updatedUser.getPrenom());
        user.setAdresse(updatedUser.getAdresse());
        user.setNum_tel(updatedUser.getNum_tel());
        user.setProfession(updatedUser.getProfession());
        user.setSpecialte(updatedUser.getSpecialte());
        repo.save(user);
        return ResponseEntity.ok("Profil mis à jour !");
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.ok("Utilisateur supprimé !");
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        Users user = repo.findById(id).orElse(null);
        if (user == null) return ResponseEntity.status(404).body("Utilisateur non trouvé");
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/users/{id}/profile")
    public ResponseEntity<?> getUserProfile(@PathVariable Long id) {
        Users user = repo.findById(id).orElse(null);
        if (user == null) return ResponseEntity.status(404).body("Utilisateur non trouvé");
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/public/{id}")
    public ResponseEntity<?> getPublicUserProfile(@PathVariable Long id) {
        Users user = repo.findById(id).orElse(null);
        if (user == null) return ResponseEntity.status(404).body("Utilisateur non trouvé");
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(@RequestParam String q) {
        try {
            List<Users> results = repo.findByNameContainingIgnoreCaseOrPrenomContainingIgnoreCaseOrProfessionContainingIgnoreCase(q, q, q);
            results.forEach(u -> u.setPassword(null));
            return ResponseEntity.ok(Map.of("users", results, "total", results.size()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/search/role")
    public ResponseEntity<?> searchByRole(@RequestParam String role) {
        try {
            Role r = Role.valueOf(role.toUpperCase());
            List<Users> results = repo.findByRole(r);
            results.forEach(u -> u.setPassword(null));
            return ResponseEntity.ok(Map.of("users", results, "total", results.size()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role: " + role));
        }
    }
}
