package tn.esprit.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.user.entity.Role;
import tn.esprit.user.entity.Users;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<Users,Long> {
    Optional<Users> findByEmail(String email);
    boolean existsByRole(Role role);
    List<Users> findByRole(Role role);
    List<Users> findByNameContainingIgnoreCaseOrPrenomContainingIgnoreCaseOrProfessionContainingIgnoreCase(String name, String prenom, String profession);
}
