package com.example.demo.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    public SchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        createCollectionTable("app_user_majors", "app_user_id", "majors");
        createCollectionTable("post_tags", "post_id", "tags");
        createCollectionTable("post_majors", "post_id", "majors");
        createCollectionTable("post_question_reproduction_steps", "post_id", "question_reproduction_steps");
        createCollectionTable("post_bug_reproduction_steps", "post_id", "bug_reproduction_steps");
        createPostBookmarksTable();
        createPostLikesTable();
        createPostCommentLikesTable();
    }

    private void createCollectionTable(String tableName, String ownerColumn, String valueColumn) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s` (
                    `id` BIGINT NOT NULL AUTO_INCREMENT,
                    `%s` BIGINT NOT NULL,
                    `%s` VARCHAR(255),
                    PRIMARY KEY (`id`),
                    INDEX `%s` (`%s`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.formatted(tableName, ownerColumn, valueColumn, "idx_" + tableName + "_owner", ownerColumn));
    }

    private void createPostBookmarksTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `post_bookmarks` (
                    `id` BIGINT NOT NULL AUTO_INCREMENT,
                    `user_id` BIGINT NOT NULL,
                    `post_id` BIGINT NOT NULL,
                    `created_at` DATETIME(6) NOT NULL,
                    PRIMARY KEY (`id`),
                    UNIQUE KEY `uk_post_bookmarks_user_post` (`user_id`, `post_id`),
                    INDEX `idx_post_bookmarks_user_created` (`user_id`, `created_at`),
                    INDEX `idx_post_bookmarks_post` (`post_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createPostLikesTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `post_likes` (
                    `id` BIGINT NOT NULL AUTO_INCREMENT,
                    `user_id` BIGINT NOT NULL,
                    `post_id` BIGINT NOT NULL,
                    `created_at` DATETIME(6) NOT NULL,
                    PRIMARY KEY (`id`),
                    UNIQUE KEY `uk_post_likes_user_post` (`user_id`, `post_id`),
                    INDEX `idx_post_likes_user_created` (`user_id`, `created_at`),
                    INDEX `idx_post_likes_post` (`post_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createPostCommentLikesTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `post_comment_likes` (
                    `id` BIGINT NOT NULL AUTO_INCREMENT,
                    `user_id` BIGINT NOT NULL,
                    `comment_id` BIGINT NOT NULL,
                    `created_at` DATETIME(6) NOT NULL,
                    PRIMARY KEY (`id`),
                    UNIQUE KEY `uk_post_comment_likes_user_comment` (`user_id`, `comment_id`),
                    INDEX `idx_post_comment_likes_user_created` (`user_id`, `created_at`),
                    INDEX `idx_post_comment_likes_comment` (`comment_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }
}
