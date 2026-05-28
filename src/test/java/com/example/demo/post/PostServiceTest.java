package com.example.demo.post;

import static com.example.demo.TestSupport.withId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.auth.AppUser;
import com.example.demo.notification.NotificationService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PostServiceTest {
    private final PostRepository postRepository = mock(PostRepository.class);
    private final PostCommentRepository commentRepository = mock(PostCommentRepository.class);
    private final PostService service = new PostService(postRepository, commentRepository, mock(NotificationService.class));

    @Test
    void createPostRejectsBlankTitleOrContent() {
        AppUser author = user(1L, "author@example.com");
        PostPayload payload = new PostPayload(" ", "body", "talk", List.of(), List.of(), null, null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.createPost(payload, author)
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatusCode());
        assertEquals("TITLE_AND_CONTENT_REQUIRED", error.getReason());
    }

    @Test
    void updatePostRejectsNonAuthor() {
        AppUser author = user(1L, "author@example.com");
        AppUser otherUser = user(2L, "other@example.com");
        Post post = withId(new Post("title", "body", "talk", author), 100L);
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        PostPayload payload = new PostPayload("updated", "body", "talk", List.of(), List.of(), null, null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.updatePost(100L, payload, otherUser)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals("POST_MODIFY_FORBIDDEN", error.getReason());
    }

    private static AppUser user(Long id, String email) {
        return withId(new AppUser(email, email, email, "hash", true, List.of()), id);
    }
}
