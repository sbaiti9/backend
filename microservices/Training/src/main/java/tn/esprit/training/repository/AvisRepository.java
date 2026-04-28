package tn.esprit.training.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tn.esprit.training.entity.Avis;

import java.util.List;

public interface AvisRepository extends JpaRepository<Avis, Long> {
    List<Avis> findByTraining_IdOrderByCreatedAtDesc(Long trainingId);
    Page<Avis> findByTraining_Id(Long trainingId, Pageable pageable);

    long countByTraining_Id(Long trainingId);

    @Query("SELECT COALESCE(MAX(a.id), 0) + 1 FROM Avis a")
    Long nextId();

    @Query("SELECT AVG(a.rating) FROM Avis a WHERE a.training.id = :trainingId")
    Double averageRatingByTrainingId(@org.springframework.data.repository.query.Param("trainingId") Long trainingId);

    @Query("SELECT AVG(a.rating) FROM Avis a")
    Double averageRatingGlobal();

    @Query("SELECT COUNT(a) FROM Avis a WHERE a.rating = :rating")
    Long countByRating(Integer rating);
}

