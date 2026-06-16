package com.example.demo.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaInitializer implements CommandLineRunner {
    private static final String INFORMATION_SCHEMA_COLUMNS_QUERY = """
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = ?
              AND column_name = ?
            """;

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
        ensurePostCounterColumns();
        backfillPostInteractionCounts();
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

    private void ensurePostCounterColumns() {
        ensureColumnExists("posts", "like_count", "INT NOT NULL DEFAULT 0");
        ensureColumnExists("posts", "bookmark_count", "INT NOT NULL DEFAULT 0");
        ensureColumnExists("posts", "view_count", "INT NOT NULL DEFAULT 0");
        ensureColumnExists("posts", "bug_watchers", "INT NOT NULL DEFAULT 0");
        ensureColumnExists("posts", "question_solved", "BIT NOT NULL DEFAULT b'0'");
        ensureColumnExists("post_comments", "like_count", "INT NOT NULL DEFAULT 0");

        jdbcTemplate.update("UPDATE `posts` SET `like_count` = 0 WHERE `like_count` IS NULL");
        jdbcTemplate.update("UPDATE `posts` SET `bookmark_count` = 0 WHERE `bookmark_count` IS NULL");
        jdbcTemplate.update("UPDATE `posts` SET `view_count` = 0 WHERE `view_count` IS NULL");
        jdbcTemplate.update("UPDATE `posts` SET `bug_watchers` = 0 WHERE `bug_watchers` IS NULL");
        jdbcTemplate.update("UPDATE `posts` SET `question_solved` = b'0' WHERE `question_solved` IS NULL");
        jdbcTemplate.update("UPDATE `post_comments` SET `like_count` = 0 WHERE `like_count` IS NULL");
    }

    private void backfillPostInteractionCounts() {
        jdbcTemplate.update("""
                UPDATE `posts` p
                LEFT JOIN (
                    SELECT `post_id`, COUNT(*) AS `like_count`
                    FROM `post_likes`
                    GROUP BY `post_id`
                ) likes ON likes.`post_id` = p.`id`
                SET p.`like_count` = COALESCE(likes.`like_count`, 0)
                """);

        jdbcTemplate.update("""
                UPDATE `posts` p
                LEFT JOIN (
                    SELECT `post_id`, COUNT(*) AS `bookmark_count`
                    FROM `post_bookmarks`
                    GROUP BY `post_id`
                ) bookmarks ON bookmarks.`post_id` = p.`id`
                SET p.`bookmark_count` = COALESCE(bookmarks.`bookmark_count`, 0)
                """);

        jdbcTemplate.update("""
                UPDATE `post_comments` c
                LEFT JOIN (
                    SELECT `comment_id`, COUNT(*) AS `like_count`
                    FROM `post_comment_likes`
                    GROUP BY `comment_id`
                ) likes ON likes.`comment_id` = c.`id`
                SET c.`like_count` = COALESCE(likes.`like_count`, 0)
                """);
    }

    private void ensureColumnExists(String tableName, String columnName, String columnDefinition) {
        Integer count = jdbcTemplate.queryForObject(
                INFORMATION_SCHEMA_COLUMNS_QUERY,
                Integer.class,
                tableName,
                columnName
        );

        if (count != null && count > 0) {
            return;
        }

        jdbcTemplate.execute("""
                ALTER TABLE `%s`
                ADD COLUMN `%s` %s
                """.formatted(tableName, columnName, columnDefinition));
    }
}
