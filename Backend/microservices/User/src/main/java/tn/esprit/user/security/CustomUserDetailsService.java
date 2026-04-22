package tn.esprit.user.security;

import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import tn.esprit.user.entity.Users;
import tn.esprit.user.repository.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository repo;

    public CustomUserDetailsService(UserRepository repo) {
        this.repo = repo;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        Users user = repo.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // roles("ADMIN") → ROLE_ADMIN avec le préfixe défini par GrantedAuthorityDefaults (SecurityConfig).
        return User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .build();
    }
}