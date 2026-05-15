-- Apply on existing databases that were already initialized.
-- Example:
-- docker exec -i ica-mysql mysql -uroot -p<password> coding_assistant < scripts/db_patch_query_retrieval_logs.sql

CREATE TABLE IF NOT EXISTS `query_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `conversation_id` BIGINT NOT NULL,
    `user_message_id` BIGINT DEFAULT NULL,
    `assistant_message_id` BIGINT DEFAULT NULL,
    `query_text` TEXT NOT NULL,
    `query_hash` VARCHAR(64) DEFAULT NULL,
    `expanded_terms` JSON DEFAULT NULL,
    `retrieval_status` VARCHAR(30) NOT NULL DEFAULT 'retrieved',
    `source_count` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_query_log_conversation_time` (`conversation_id`, `created_at`),
    INDEX `idx_query_log_user_time` (`user_id`, `created_at`),
    CONSTRAINT `fk_query_log_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_query_log_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `retrieval_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `query_log_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `conversation_id` BIGINT NOT NULL,
    `assistant_message_id` BIGINT DEFAULT NULL,
    `source_type` VARCHAR(20) NOT NULL,
    `source_id` VARCHAR(255) DEFAULT NULL,
    `source_title` VARCHAR(255) DEFAULT NULL,
    `excerpt` TEXT DEFAULT NULL,
    `score` DOUBLE DEFAULT NULL,
    `chunk_index` INT DEFAULT NULL,
    `metadata` JSON DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_retrieval_log_query` (`query_log_id`),
    INDEX `idx_retrieval_log_conversation_time` (`conversation_id`, `created_at`),
    CONSTRAINT `fk_retrieval_log_query` FOREIGN KEY (`query_log_id`) REFERENCES `query_log` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_retrieval_log_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_retrieval_log_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

