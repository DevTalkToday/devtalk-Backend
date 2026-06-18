package com.example.demo.admin;

import static com.example.demo.TestSupport.withId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.demo.auth.AppUser;
import com.example.demo.auth.AuthTokenRepository;
import com.example.demo.auth.OAuthAccountRepository;
import com.example.demo.auth.UserRepository;
import com.example.demo.follow.FollowRepository;
import com.example.demo.friend.FriendshipRepository;
import com.example.demo.message.MessageRepository;
import com.example.demo.notification.NotificationRepository;
import com.example.demo.post.PostBookmarkRepository;
import com.example.demo.post.PostCommentLikeRepository;
import com.example.demo.post.PostCommentRepository;
import com.example.demo.post.PostLikeRepository;
import com.example.demo.post.PostRepository;
import com.example.demo.report.ReportRepository;
import com.example.demo.settings.NotificationPreferenceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminServiceTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PostRepository postRepository = mock(PostRepository.class);
    private final PostCommentRepository commentRepository = mock(PostCommentRepository.class);
    private final AdminService service = new AdminService(
            userRepository,
            postRepository,
            commentRepository,
            mock(PostBookmarkRepository.class),
            mock(PostCommentLikeRepository.class),
            mock(PostLikeRepository.class),
            mock(AuthTokenRepository.class),
            mock(OAuthAccountRepository.class),
            mock(FriendshipRepository.class),
            mock(FollowRepository.class),
            mock(MessageRepository.class),
            mock(NotificationRepository.class),
            mock(NotificationPreferenceRepository.class),
            mock(ReportRepository.class)
    );

    @Test
    void deleteUserMajorRequiresAdmin() {
        AppUser actor = user(1L, "user@example.com", List.of());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.deleteUserMajor(actor, 2L, "backend")
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals("ADMIN_REQUIRED", error.getReason());
        verifyNoInteractions(userRepository);
    }

    @Test
    void deleteUserMajorRemovesMatchingMajor() {
        AppUser actor = user(1L, "s25002@gsm.hs.kr", List.of());
        AppUser target = user(2L, "target@example.com", List.of("backend", "frontend"));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        service.deleteUserMajor(actor, 2L, " backend ");

        assertEquals(List.of("frontend"), target.getMajors());
    }

    @Test
    void deleteUserMajorRejectsBlankMajor() {
        AppUser actor = user(1L, "s25002@gsm.hs.kr", List.of());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.deleteUserMajor(actor, 2L, " ")
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatusCode());
        assertEquals("MAJOR_REQUIRED", error.getReason());
        verifyNoInteractions(userRepository);
    }

    private static AppUser user(Long id, String email, List<String> majors) {
        return withId(new AppUser(email, email, email, "hash", true, majors), id);
    }
}
