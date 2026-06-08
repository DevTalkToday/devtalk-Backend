package com.example.demo.post;

import static com.example.demo.TestSupport.withId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    private final PostBookmarkRepository bookmarkRepository = mock(PostBookmarkRepository.class);
    private final PostService service = new PostService(postRepository, commentRepository, bookmarkRepository, mock(NotificationService.class));

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

    @Test
    void getPostHidesPrivatePostFromNonAuthor() {
        AppUser author = user(1L, "author@example.com");
        AppUser viewer = user(2L, "viewer@example.com");
        Post post = withId(new Post("title", "body", "talk", author), 100L);

        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.getPost(100L, false, viewer)
        );

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertEquals("NOT_FOUND", error.getReason());
    }

    @Test
    void getPostAllowsAuthorToReadPrivatePost() {
        AppUser author = user(1L, "author@example.com");
        Post post = withId(new Post("title", "body", "talk", author), 100L);

        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        PostResponse response = service.getPost(100L, false, author);

        assertEquals("100", response.id());
        assertEquals("talk", response.category());
    }

    @Test
    void createQnaPostAlwaysMarksSolvedAndStoresNewFields() {
        AppUser author = user(1L, "author@example.com");
        PostPayload payload = new PostPayload(
                "Resolved cache issue",
                "body",
                "qna",
                List.of("nextjs"),
                List.of("프론트엔드"),
                new PostPayload.QuestionPayload(
                        "Fresh data should be shown after deploy.",
                        "Stale data was shown on first load.",
                        List.of("Open page", "Observe stale response", "Refresh after cache invalidation"),
                        null
                ),
                null
        );
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = service.createPost(payload, author);

        assertEquals("qna", response.category());
        assertTrue(response.question().solved());
        assertEquals("Fresh data should be shown after deploy.", response.question().expected());
        assertEquals("Stale data was shown on first load.", response.question().actual());
        assertEquals(3, response.question().reproductionSteps().size());
    }

    @Test
    void createCommentHidesPrivatePostFromNonAuthor() {
        AppUser author = user(1L, "author@example.com");
        AppUser viewer = user(2L, "viewer@example.com");
        Post post = withId(new Post("title", "body", "talk", author), 100L);

        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.createComment(100L, new CommentPayload("hello"), viewer)
        );

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertEquals("NOT_FOUND", error.getReason());
    }

    @Test
    void bookmarkPostCreatesBookmarkAndMarksResponse() {
        AppUser author = user(1L, "author@example.com");
        AppUser viewer = user(2L, "viewer@example.com");
        Post post = withId(new Post("title", "body", "bug", author), 100L);
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(bookmarkRepository.findByPostAndUser(post, viewer)).thenReturn(Optional.empty());
        when(bookmarkRepository.existsByPostAndUser(post, viewer)).thenReturn(true);

        PostResponse response = service.bookmarkPost(100L, viewer);

        assertEquals(1, response.bookmarkCount());
        assertTrue(response.bookmarked());
        verify(bookmarkRepository).save(any(PostBookmark.class));
    }

    private static AppUser user(Long id, String email) {
        return withId(new AppUser(email, email, email, "hash", true, List.of()), id);
    }
}
