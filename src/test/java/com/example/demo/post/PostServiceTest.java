package com.example.demo.post;

import static com.example.demo.TestSupport.withId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.auth.AppUser;
import com.example.demo.notification.NotificationService;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PostServiceTest {
    private final PostRepository postRepository = mock(PostRepository.class);
    private final PostCommentRepository commentRepository = mock(PostCommentRepository.class);
    private final PostBookmarkRepository bookmarkRepository = mock(PostBookmarkRepository.class);
    private final PostLikeRepository likeRepository = mock(PostLikeRepository.class);
    private final PostCommentLikeRepository commentLikeRepository = mock(PostCommentLikeRepository.class);
    private final PostViewRepository postViewRepository = mock(PostViewRepository.class);
    private final PostService service = new PostService(
            postRepository,
            commentRepository,
            bookmarkRepository,
            likeRepository,
            commentLikeRepository,
            postViewRepository,
            mock(NotificationService.class)
    );

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
    void createPostClampsTitleAndContentLengths() {
        AppUser author = user(1L, "author@example.com");
        String longTitle = String.join("", Collections.nCopies(130, "t"));
        String longContent = String.join("", Collections.nCopies(2105, "c"));
        PostPayload payload = new PostPayload(longTitle, longContent, "talk", List.of(), List.of(), null, null);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = service.createPost(payload, author);

        assertEquals(100, response.title().length());
        assertEquals(2000, response.content().length());
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
    void getPostMarksAdminAsAbleToDeleteOthersPost() {
        AppUser author = user(1L, "author@example.com");
        AppUser admin = user(2L, "s25002@gsm.hs.kr");
        Post post = withId(new Post("title", "body", "bug", author), 100L);
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        PostResponse response = service.getPost(100L, false, admin);

        assertTrue(response.canDelete());
    }

    @Test
    void deletePostAllowsAdminToDeleteOthersPost() {
        AppUser author = user(1L, "author@example.com");
        AppUser admin = user(2L, "s25002@gsm.hs.kr");
        Post post = withId(new Post("title", "body", "talk", author), 100L);
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        service.deletePost(100L, admin);

        verify(postViewRepository).deleteByPost(post);
        verify(postRepository).delete(post);
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
    void getPostDoesNotIncreaseViewCountForAuthor() {
        AppUser author = user(1L, "author@example.com");
        Post post = withId(new Post("title", "body", "bug", author), 100L);
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        PostResponse response = service.getPost(100L, true, author);

        assertEquals(0, response.viewCount());
        verifyNoInteractions(postViewRepository);
    }

    @Test
    void getPostIncreasesViewCountForFirstViewByAnotherUser() {
        AppUser author = user(1L, "author@example.com");
        AppUser viewer = user(2L, "viewer@example.com");
        Post post = withId(new Post("title", "body", "bug", author), 100L);
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(postViewRepository.findByUserAndPost(viewer, post)).thenReturn(Optional.empty());
        when(postViewRepository.saveAndFlush(any(PostView.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = service.getPost(100L, true, viewer);

        assertEquals(1, response.viewCount());
        verify(postViewRepository).saveAndFlush(any(PostView.class));
    }

    @Test
    void getPostDoesNotIncreaseViewCountWithinTwelveHours() {
        AppUser author = user(1L, "author@example.com");
        AppUser viewer = user(2L, "viewer@example.com");
        Post post = withId(new Post("title", "body", "bug", author), 100L);
        PostView view = new PostView(viewer, post, Instant.now().minusSeconds(11 * 60 * 60));
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(postViewRepository.findByUserAndPost(viewer, post)).thenReturn(Optional.of(view));

        PostResponse response = service.getPost(100L, true, viewer);

        assertEquals(0, response.viewCount());
    }

    @Test
    void getPostIncreasesViewCountAgainAfterTwelveHours() {
        AppUser author = user(1L, "author@example.com");
        AppUser viewer = user(2L, "viewer@example.com");
        Post post = withId(new Post("title", "body", "bug", author), 100L);
        Instant previousViewedAt = Instant.now().minusSeconds(13 * 60 * 60);
        PostView view = new PostView(viewer, post, previousViewedAt);
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(postViewRepository.findByUserAndPost(viewer, post)).thenReturn(Optional.of(view));

        PostResponse response = service.getPost(100L, true, viewer);

        assertEquals(1, response.viewCount());
        assertTrue(view.getViewedAt().isAfter(previousViewedAt));
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
    void getPostExcerptNormalizesSupportedHtmlMarkup() {
        AppUser author = user(1L, "author@example.com");
        Post post = withId(new Post("title", "<h1>Hello</h1>\nFirst line<br>Second line<hr />Done", "bug", author), 100L);
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        PostResponse response = service.getPost(100L, false, author);

        assertEquals("Hello First line Second line Done", response.excerpt());
    }

    @Test
    void updateClosedBugPostConvertsToQnaAndKeepsSharedFields() {
        AppUser author = user(1L, "author@example.com");
        Post post = withId(new Post("title", "body", "bug", author), 100L);
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        PostPayload payload = new PostPayload(
                "Resolved login issue",
                "body",
                "bug",
                List.of("auth"),
                List.of("backend"),
                null,
                new PostPayload.BugPayload(
                        "closed",
                        "User should stay signed in after refresh.",
                        "Session was cleared after refresh.",
                        List.of("Login", "Refresh the page", "Observe logout"),
                        3,
                        "77"
                )
        );

        PostResponse response = service.updatePost(100L, payload, author);

        assertEquals("qna", response.category());
        assertTrue(response.question().solved());
        assertEquals("User should stay signed in after refresh.", response.question().expected());
        assertEquals("Session was cleared after refresh.", response.question().actual());
        assertEquals(List.of("Login", "Refresh the page", "Observe logout"), response.question().reproductionSteps());
        assertEquals("77", response.question().acceptedCommentId());
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

    @Test
    void likePostCreatesLikeAndMarksResponse() {
        AppUser author = user(1L, "author@example.com");
        AppUser viewer = user(2L, "viewer@example.com");
        Post post = withId(new Post("title", "body", "bug", author), 100L);
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(likeRepository.findByPostAndUser(post, viewer)).thenReturn(Optional.empty());
        when(likeRepository.existsByPostAndUser(post, viewer)).thenReturn(true);

        PostResponse response = service.likePost(100L, viewer);

        assertEquals(1, response.likeCount());
        assertTrue(response.liked());
        verify(likeRepository).save(any(PostLike.class));
    }

    @Test
    void unlikePostRemovesLikeAndClearsResponse() {
        AppUser author = user(1L, "author@example.com");
        AppUser viewer = user(2L, "viewer@example.com");
        Post post = withId(new Post("title", "body", "bug", author), 100L);
        PostLike like = withId(new PostLike(viewer, post), 200L);
        post.incrementLikeCount();
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(likeRepository.findByPostAndUser(post, viewer)).thenReturn(Optional.of(like));
        when(likeRepository.existsByPostAndUser(post, viewer)).thenReturn(false);

        PostResponse response = service.unlikePost(100L, viewer);

        assertEquals(0, response.likeCount());
        assertEquals(false, response.liked());
        verify(likeRepository).delete(like);
    }

    @Test
    void likeCommentCreatesLikeAndMarksResponse() {
        AppUser author = user(1L, "author@example.com");
        AppUser viewer = user(2L, "viewer@example.com");
        Post post = withId(new Post("title", "body", "bug", author), 100L);
        PostComment comment = withId(new PostComment(post, author, "comment"), 200L);
        post.addComment(comment);
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(commentRepository.findByIdAndPostId(200L, 100L)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.findByCommentAndUser(comment, viewer)).thenReturn(Optional.empty());
        when(commentLikeRepository.findLikedCommentIds(viewer, List.of(200L))).thenReturn(List.of(200L));

        PostResponse response = service.likeComment(100L, 200L, viewer);

        assertEquals(1, response.comments().getFirst().likeCount());
        assertTrue(response.comments().getFirst().liked());
        verify(commentLikeRepository).save(any(PostCommentLike.class));
    }

    @Test
    void unlikeCommentRemovesLikeAndClearsResponse() {
        AppUser author = user(1L, "author@example.com");
        AppUser viewer = user(2L, "viewer@example.com");
        Post post = withId(new Post("title", "body", "bug", author), 100L);
        PostComment comment = withId(new PostComment(post, author, "comment"), 200L);
        PostCommentLike like = withId(new PostCommentLike(viewer, comment), 300L);
        comment.incrementLikeCount();
        post.addComment(comment);
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(commentRepository.findByIdAndPostId(200L, 100L)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.findByCommentAndUser(comment, viewer)).thenReturn(Optional.of(like));
        when(commentLikeRepository.findLikedCommentIds(viewer, List.of(200L))).thenReturn(List.of());

        PostResponse response = service.unlikeComment(100L, 200L, viewer);

        assertEquals(0, response.comments().getFirst().likeCount());
        assertEquals(false, response.comments().getFirst().liked());
        verify(commentLikeRepository).delete(like);
    }

    private static AppUser user(Long id, String email) {
        return withId(new AppUser(email, email, email, "hash", true, List.of()), id);
    }
}
