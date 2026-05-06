package tn.esprit.user.repository;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import tn.esprit.user.entity.Users;

import java.util.List;

public interface UsersSearchRepository extends ElasticsearchRepository<Users, String> {
    List<Users> findByNameContainingOrDescriptionContaining(String a, String b);
}

