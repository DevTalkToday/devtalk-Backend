package com.example.demo.profile;

import static com.example.demo.TestSupport.withId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.auth.AppUser;
import com.example.demo.auth.UserRepository;
import com.example.demo.post.Post;
import com.example.demo.post.PostBookmark;
import com.example.demo.post.PostBookmarkRepository;
import com.example.demo.post.PostComment;
import com.example.demo.post.PostCommentRepository;
import com.example.demo.post.PostLikeRepository;
import com.example.demo.post.PostListResponse;
import com.example.demo.post.PostRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class ProfileServiceTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PostRepository postRepository = mock(PostRepository.class);
    private final PostCommentRepository commentRepository = mock(PostCommentRepository.class);
    private final PostBookmarkRepository bookmarkRepository = mock(PostBookmarkRepository.class);
    private final PostLikeRepository likeRepository = mock(PostLikeRepository.class);
    private final ProfileService service = new ProfileService(userRepository, postRepository, commentRepository, bookmarkRepository, likeRepository);

    @Test
    void publicProfileExcludesPrivatePostAndCommentCounts() {
        AppUser user = user(1L, "user@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.countByAuthorAndCategoryNot(user, "talk")).thenReturn(2L);
        when(commentRepository.countByAuthorAndPostCategoryNot(user, "talk")).thenReturn(1L);
        when(commentRepository.countAcceptedByAuthor(user)).thenReturn(0L);

        PublicProfileResponse response = service.getPublicProfile(1L);

        assertEquals(2L, response.postCount());
        assertEquals(1L, response.commentCount());
        assertEquals(0L, response.acceptedCommentCount());
    }

    @Test
    void publicProfileListsExcludePrivateTalkEntries() {
        AppUser user = user(1L, "user@example.com");
        Post publicPost = withId(new Post("public", "body", "bug", user), 10L);
        PostComment publicComment = withId(new PostComment(publicPost, user, "comment"), 20L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.countByAuthorAndCategoryNot(user, "talk")).thenReturn(1L);
        when(postRepository.findByAuthorAndCategoryNotOrderByCreatedAtDesc(user, "talk", PageRequest.of(0, 24)))
                .thenReturn(List.of(publicPost));
        when(commentRepository.countByAuthorAndPostCategoryNot(user, "talk")).thenReturn(1L);
        when(commentRepository.findByAuthorAndPostCategoryNotOrderByCreatedAtDesc(user, "talk", PageRequest.of(0, 24)))
                .thenReturn(List.of(publicComment));

        PostListResponse posts = service.listPosts(1L, 1, 24);
        ProfileCommentListResponse comments = service.listComments(1L, 1, 24);

        assertEquals(1, posts.items().size());
        assertEquals("10", posts.items().getFirst().id());
        assertEquals(1, comments.items().size());
        assertEquals("20", comments.items().getFirst().id());
        verify(postRepository).findByAuthorAndCategoryNotOrderByCreatedAtDesc(user, "talk", PageRequest.of(0, 24));
        verify(commentRepository).findByAuthorAndPostCategoryNotOrderByCreatedAtDesc(user, "talk", PageRequest.of(0, 24));
    }

    @Test
    void bookmarksListReturnsBookmarkedPosts() {
        AppUser user = user(1L, "user@example.com");
        Post post = withId(new Post("bookmarked", "body", "bug", user), 10L);
        PostBookmark bookmark = withId(new PostBookmark(user, post), 30L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bookmarkRepository.findReadableByUser(user, PageRequest.of(0, 24, org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("createdAt"),
                org.springframework.data.domain.Sort.Order.desc("id")
        )))).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(bookmark), PageRequest.of(0, 24), 1));

        PostListResponse response = service.listBookmarks(user, 1, 24);

        assertEquals(1, response.items().size());
        assertEquals("10", response.items().getFirst().id());
        assertEquals(true, response.items().getFirst().bookmarked());
    }

    private static AppUser user(Long id, String email) {
        return withId(new AppUser(email, email, email, "hash", true, List.of()), id);
    }
}
