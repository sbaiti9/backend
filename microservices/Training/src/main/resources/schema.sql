-- Legacy duplicate table cleanup (old plural name)
DROP TABLE IF EXISTS trainings;

-- Parent table must exist before avis (FK references training.id)
CREATE TABLE IF NOT EXISTS training (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(255) NOT NULL,
    level VARCHAR(255) NOT NULL,
    price DECIMAL(19, 2) NOT NULL,
    thumbnail_url VARCHAR(255),
    language VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Reviews (avis) linked to training — created after training
CREATE TABLE IF NOT EXISTS avis (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rating INT NOT NULL,
    comment TEXT,
    author_name VARCHAR(255) NOT NULL,
    training_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_training_avis
        FOREIGN KEY (training_id)
        REFERENCES training (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
