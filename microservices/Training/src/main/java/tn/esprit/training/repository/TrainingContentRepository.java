package tn.esprit.training.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.esprit.training.entity.TrainingContent;

import java.util.List;
import java.util.Optional;

public interface TrainingContentRepository extends JpaRepository<TrainingContent, Long> {
    @Query("SELECT tc FROM TrainingContent tc JOIN FETCH tc.training WHERE tc.id = :id")
    Optional<TrainingContent> findByIdWithTraining(@Param("id") Long id);

    List<TrainingContent> findByTraining_Id(Long trainingId);

    @Query(value = "SELECT COUNT(*) FROM training_content tc WHERE tc.training_id = :trainingId", nativeQuery = true)
    long countByTrainingIdNative(@Param("trainingId") Long trainingId);

    @Query(value = "SELECT tc.id FROM training_content tc WHERE tc.training_id = :trainingId", nativeQuery = true)
    List<Long> findIdsByTrainingIdNative(@Param("trainingId") Long trainingId);
}

