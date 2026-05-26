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
        createCollectionTable("post_bug_reproduction_steps", "post_id", "bug_reproduction_steps");
        createCollectionTable("post_bug_labels", "post_id", "bug_labels");
    }

    private void createCollectionTable(String tableName, String ownerColumn, String valueColumn) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s` (
                    `%s` BIGINT NOT NULL,
                    `%s` VARCHAR(255)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.formatted(tableName, ownerColumn, valueColumn));
    }
}
