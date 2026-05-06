package tn.esprit.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.user.entity.Users;
import tn.esprit.user.repository.UserRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UsersCachedService {
    private final UserRepository repo;
    private final SearchService searchService;

    @Cacheable(value = "users-all", key = "'all'")
    public List<Users> findAll() {
        return repo.findAll();
    }

    @Cacheable(value = "users", key = "#id")
    public Optional<Users> findById(Long id) {
        return repo.findById(id);
    }

    @Cacheable(value = "users-byEmail", key = "#email")
    public Optional<Users> findByEmail(String email) {
        return repo.findByEmail(email);
    }

    @Transactional
    @CacheEvict(value = {"users", "users-all", "users-byEmail"}, allEntries = true)
    public Users saveAndIndex(Users user) {
        Users saved = repo.save(user);
        searchService.indexUser(saved);
        return saved;
    }

    @Transactional
    @CacheEvict(value = {"users", "users-all", "users-byEmail"}, allEntries = true)
    public void deleteByIdAndDeindex(Long id) {
        repo.deleteById(id);
        searchService.deleteFromIndex(id);
    }
}

