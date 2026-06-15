package com.example.demo.admin;

import com.example.demo.auth.AdminAccess;
import com.example.demo.auth.AppUser;
import com.example.demo.auth.AuthTokenRepository;
import com.example.demo.auth.OAuthAccountRepository;
import com.example.demo.auth.UserRepository;
import com.example.demo.follow.FollowRepository;
import com.example.demo.friend.FriendshipRepository;
import com.example.demo.message.MessageRepository;
import com.example.demo.notification.NotificationRepository;
import com.example.demo.post.Post;
import com.example.demo.post.PostBookmarkRepository;
import com.example.demo.post.PostComment;
import com.example.demo.post.PostCommentRepository;
import com.example.demo.post.PostCommentLikeRepository;
import com.example.demo.post.PostLikeRepository;
import com.example.demo.post.PostRepository;
import com.example.demo.report.ReportRepository;
import com.example.demo.settings.NotificationPreferenceRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AdminService {
    private static final String GUEST_USERNAME = "__guest__";

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostCommentRepository commentRepository;
    private final PostBookmarkRepository postBookmarkRepository;
    private final PostCommentLikeRepository postCommentLikeRepository;
    private final PostLikeRepository postLikeRepository;
    private final AuthTokenRepository authTokenRepository;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final FriendshipRepository friendshipRepository;
    private final FollowRepository followRepository;
    private final MessageRepository messageRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final ReportRepository reportRepository;

    public AdminService(
            UserRepository userRepository,
            PostRepository postRepository,
            PostCommentRepository commentRepository,
            PostBookmarkRepository postBookmarkRepository,
            PostCommentLikeRepository postCommentLikeRepository,
            PostLikeRepository postLikeRepository,
            AuthTokenRepository authTokenRepository,
            OAuthAccountRepository oAuthAccountRepository,
            FriendshipRepository friendshipRepository,
            FollowRepository followRepository,
            MessageRepository messageRepository,
            NotificationRepository notificationRepository,
            NotificationPreferenceRepository notificationPreferenceRepository,
            ReportRepository reportRepository
    ) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.postBookmarkRepository = postBookmarkRepository;
        this.postCommentLikeRepository = postCommentLikeRepository;
        this.postLikeRepository = postLikeRepository;
        this.authTokenRepository = authTokenRepository;
        this.oAuthAccountRepository = oAuthAccountRepository;
        this.friendshipRepository = friendshipRepository;
        this.followRepository = followRepository;
        this.messageRepository = messageRepository;
        this.notificationRepository = notificationRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.reportRepository = reportRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers(AppUser actor) {
        AdminAccess.requireAdmin(actor);

        List<AppUser> users = userRepository.findAll()
                .stream()
                .filter(user -> !GUEST_USERNAME.equalsIgnoreCase(user.getUsername()))
                .sorted(Comparator.comparing(AppUser::getCreatedAt).reversed())
                .toList();
        if (users.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = users.stream().map(AppUser::getId).toList();
        Map<Long, Long> postCounts = toCountMap(postRepository.countByAuthorIds(userIds));
        Map<Long, Long> commentCounts = toCountMap(commentRepository.countByAuthorIds(userIds));

        return users.stream()
                .map(user -> AdminUserResponse.from(
                        user,
                        postCounts.getOrDefault(user.getId(), 0L),
                        commentCounts.getOrDefault(user.getId(), 0L)
                ))
                .toList();
    }

    @Transactional
    public UserDeleteResponse deleteUser(AppUser actor, Long userId) {
        AdminAccess.requireAdmin(actor);

        AppUser target = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        if (actor.getId().equals(target.getId()) || AdminAccess.isAdmin(target)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_USER_DELETE_FORBIDDEN");
        }
        if (GUEST_USERNAME.equalsIgnoreCase(target.getUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "GUEST_USER_DELETE_FORBIDDEN");
        }

        deleteUserReports(target);
        deleteUserComments(target);
        deleteUserPosts(target);

        authTokenRepository.deleteByUser(target);
        oAuthAccountRepository.deleteByUser(target);
        notificationPreferenceRepository.deleteByUser(target);
        postBookmarkRepository.deleteByUser(target);
        postCommentLikeRepository.deleteByUser(target);
        postLikeRepository.deleteByUser(target);
        notificationRepository.deleteByRecipientOrActor(target, target);
        messageRepository.deleteBySenderOrRecipient(target, target);
        friendshipRepository.deleteByRequesterOrAddressee(target, target);
        followRepository.deleteByFollowerOrFollowee(target, target);

        userRepository.delete(target);
        return new UserDeleteResponse(userId, "deleted");
    }

    private void deleteUserComments(AppUser target) {
        List<PostComment> comments = commentRepository.findByAuthor(target);
        for (PostComment comment : comments) {
            Post post = comment.getPost();
            reportRepository.deleteByTargetTypeAndTargetId("comment", String.valueOf(comment.getId()));
            postCommentLikeRepository.deleteByComment(comment);
            if (post.getAcceptedCommentId() != null && post.getAcceptedCommentId().equals(comment.getId())) {
                post.setAcceptedCommentId(null);
            }
            post.removeComment(comment);
        }
    }

    private void deleteUserPosts(AppUser target) {
        List<Post> posts = postRepository.findByAuthor(target);
        for (Post post : posts) {
            reportRepository.deleteByTargetTypeAndTargetId("post", String.valueOf(post.getId()));
            postCommentLikeRepository.deleteByCommentPost(post);
            postLikeRepository.deleteByPost(post);
            postBookmarkRepository.deleteByPost(post);
            for (PostComment comment : List.copyOf(post.getComments())) {
                reportRepository.deleteByTargetTypeAndTargetId("comment", String.valueOf(comment.getId()));
            }
        }
        postRepository.deleteAll(posts);
    }

    private void deleteUserReports(AppUser target) {
        reportRepository.deleteByReporterId(target.getId());
        reportRepository.deleteByTargetTypeAndTargetId("profile", String.valueOf(target.getId()));
    }

    private static Map<Long, Long> toCountMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
                row -> ((Number) row[0]).longValue(),
                row -> ((Number) row[1]).longValue()
        ));
    }
}
