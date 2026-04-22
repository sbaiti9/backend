-- Clean up legacy duplicate table if exists
DROP TABLE IF EXISTS trainings;

-- Reviews table (Avis) linked to training
CREATE TABLE IF NOT EXISTS avis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rating INT NOT NULL,
    comment TEXT,
    author_name VARCHAR(255),
    training_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_training_avis
        FOREIGN KEY (training_id)
        REFERENCES training(id)
        ON DELETE CASCADE
);

