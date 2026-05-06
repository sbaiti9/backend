package esprit.entreprise.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * Feign Client pour communiquer avec le User microservice via Gateway
 * Récupère les informations de l'utilisateur (éducation, rating, statut)
 */
@FeignClient(name = "USER", url = "http://api-gateway:8091/user")
public interface UserServiceClient {

    /**
     * Récupérer les infos utilisateur par ID
     * GET /user/users/{id}
     */
    @GetMapping("/users/{id}")
    Map<String, Object> getUserById(@PathVariable("id") Long userId);

    /**
     * Récupérer le niveau d'éducation d'un utilisateur
     * GET /user/users/{id}/education
     */
    @GetMapping("/users/{id}/education")
    Map<String, String> getUserEducation(@PathVariable("id") Long userId);

    /**
     * Récupérer le statut d'un utilisateur
     * GET /user/users/{id}/status
     */
    @GetMapping("/users/{id}/status")
    Map<String, String> getUserStatus(@PathVariable("id") Long userId);

    /**
     * Récupérer la notation moyenne d'un utilisateur
     * GET /user/users/{id}/rating
     */
    @GetMapping("/users/{id}/rating")
    Map<String, Double> getUserRating(@PathVariable("id") Long userId);
}