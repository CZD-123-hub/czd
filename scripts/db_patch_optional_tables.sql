-- Ensure optional/recent tables exist to avoid runtime 500 on older databases.
-- Safe to run multiple times.

CREATE TABLE IF NOT EXISTS `knowledge_chunk` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `document_id` BIGINT NOT NULL,
    `chunk_index` INT NOT NULL,
    `content` TEXT NOT NULL,
    `embedding` JSON DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_chunk_doc_idx` (`document_id`, `chunk_index`),
    INDEX `idx_chunk_document_id` (`document_id`),
    CONSTRAINT `fk_chunk_document` FOREIGN KEY (`document_id`) REFERENCES `knowledge_document` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `learning_video` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `title` VARCHAR(200) NOT NULL,
    `description` TEXT DEFAULT NULL,
    `platform` VARCHAR(50) DEFAULT 'web',
    `url` VARCHAR(500) NOT NULL,
    `cover_url` VARCHAR(500) DEFAULT NULL,
    `duration_seconds` INT DEFAULT 0,
    `knowledge_id` VARCHAR(100) DEFAULT NULL,
    `tags` JSON DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_learning_video_knowledge_id` (`knowledge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `learning_video_history` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `video_id` BIGINT NOT NULL,
    `watched_seconds` INT NOT NULL DEFAULT 0,
    `completed` TINYINT(1) NOT NULL DEFAULT 0,
    `last_watched_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_video_history_user_video` (`user_id`, `video_id`),
    INDEX `idx_video_history_user_time` (`user_id`, `last_watched_at`),
    CONSTRAINT `fk_video_history_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_video_history_video` FOREIGN KEY (`video_id`) REFERENCES `learning_video` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `learning_video_favorite` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `video_id` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_video_favorite_user_video` (`user_id`, `video_id`),
    INDEX `idx_video_favorite_user` (`user_id`),
    CONSTRAINT `fk_video_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_video_favorite_video` FOREIGN KEY (`video_id`) REFERENCES `learning_video` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `knowledge_document_favorite` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `document_id` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_doc_favorite_user_doc` (`user_id`, `document_id`),
    INDEX `idx_doc_favorite_user` (`user_id`),
    CONSTRAINT `fk_doc_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_doc_favorite_document` FOREIGN KEY (`document_id`) REFERENCES `knowledge_document` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `practice_session` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `path_id` BIGINT DEFAULT NULL,
    `mode` VARCHAR(20) NOT NULL DEFAULT 'practice',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ongoing',
    `total_questions` INT NOT NULL DEFAULT 0,
    `answered_count` INT NOT NULL DEFAULT 0,
    `correct_count` INT NOT NULL DEFAULT 0,
    `duration_minutes` INT DEFAULT NULL,
    `score` DECIMAL(5,2) DEFAULT NULL,
    `grade` VARCHAR(20) DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `submitted_at` DATETIME DEFAULT NULL,
    INDEX `idx_practice_session_user_mode` (`user_id`, `mode`),
    INDEX `idx_practice_session_path` (`path_id`),
    CONSTRAINT `fk_practice_session_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_practice_session_path` FOREIGN KEY (`path_id`) REFERENCES `learning_path` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `practice_question` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `session_id` BIGINT NOT NULL,
    `knowledge_id` VARCHAR(100) DEFAULT NULL,
    `question_stem` TEXT NOT NULL,
    `options_json` JSON DEFAULT NULL,
    `correct_answer` VARCHAR(20) NOT NULL,
    `explanation` TEXT DEFAULT NULL,
    `user_answer` VARCHAR(20) DEFAULT NULL,
    `is_correct` TINYINT(1) DEFAULT NULL,
    `question_order` INT NOT NULL DEFAULT 1,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_practice_question_session` (`session_id`),
    CONSTRAINT `fk_practice_question_session` FOREIGN KEY (`session_id`) REFERENCES `practice_session` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'learning_node'
      AND COLUMN_NAME = 'custom_name'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE `learning_node` ADD COLUMN `custom_name` VARCHAR(200) DEFAULT NULL AFTER `knowledge_id`',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
