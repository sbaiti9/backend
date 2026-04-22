package tn.esprit.training.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.training.entity.Training;

public interface TrainingRepository extends JpaRepository<Training, Long> {
}

