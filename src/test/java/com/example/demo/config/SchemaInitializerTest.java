package com.example.demo.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SchemaInitializerTest {
    @Test
    void runCreatesMissingInteractionColumnsAndBackfillsCounts() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), any(), any())).thenReturn(0);

        SchemaInitializer initializer = new SchemaInitializer(jdbcTemplate);

        initializer.run();

        verify(jdbcTemplate, times(6)).execute(org.mockito.ArgumentMatchers.startsWith("ALTER TABLE"));
        verify(jdbcTemplate).update("UPDATE `posts` SET `like_count` = 0 WHERE `like_count` IS NULL");
        verify(jdbcTemplate).update("UPDATE `posts` SET `bookmark_count` = 0 WHERE `bookmark_count` IS NULL");
        verify(jdbcTemplate).update("UPDATE `posts` SET `view_count` = 0 WHERE `view_count` IS NULL");
        verify(jdbcTemplate).update("UPDATE `posts` SET `bug_watchers` = 0 WHERE `bug_watchers` IS NULL");
        verify(jdbcTemplate).update("UPDATE `posts` SET `question_solved` = b'0' WHERE `question_solved` IS NULL");
        verify(jdbcTemplate).update("UPDATE `post_comments` SET `like_count` = 0 WHERE `like_count` IS NULL");
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("FROM `post_likes`"));
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("FROM `post_bookmarks`"));
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("FROM `post_comment_likes`"));
    }

    @Test
    void runSkipsColumnCreationWhenInteractionColumnsAlreadyExist() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), any(), any())).thenReturn(1);

        SchemaInitializer initializer = new SchemaInitializer(jdbcTemplate);

        initializer.run();

        verify(jdbcTemplate, never()).execute(org.mockito.ArgumentMatchers.startsWith("ALTER TABLE"));
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("FROM `post_likes`"));
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("FROM `post_bookmarks`"));
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("FROM `post_comment_likes`"));
    }
}
