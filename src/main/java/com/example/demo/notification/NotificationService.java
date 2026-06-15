package com.example.demo.notification;

import com.example.demo.auth.AppUser;
import com.example.demo.follow.Follow;
import com.example.demo.follow.FollowRepository;
import com.example.demo.post.Post;
import com.example.demo.post.PostComment;
import com.example.demo.post.PostContentText;
import com.example.demo.settings.NotificationPreference;
import com.example.demo.settings.NotificationPreferenceRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final FollowRepository followRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationPreferenceRepository notificationPreferenceRepository,
            FollowRepository followRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.followRepository = followRepository;
    }

    @Transactional
    public List<NotificationResponse> list(AppUser currentUser, int limit) {
        return notificationRepository.findByRecipient(currentUser, PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public NotificationUnreadCountResponse unreadCount(AppUser currentUser) {
        return new NotificationUnreadCountResponse(notificationRepository.countByRecipientAndReadAtIsNull(currentUser));
    }

    @Transactional
    public NotificationResponse markRead(AppUser currentUser, Long id) {
        Notification notification = notificationRepository.findByIdAndRecipient(id, currentUser)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.markRead();
        return NotificationResponse.from(notification);
    }

    @Transactional
    public NotificationReadAllResponse markAllRead(AppUser currentUser) {
        List<Notification> unread = notificationRepository.findByRecipientAndReadAtIsNull(currentUser);
        unread.forEach(Notification::markRead);
        return new NotificationReadAllResponse(unread.size());
    }

    @Transactional
    public void createPostCommentNotification(Post post, PostComment comment) {
        AppUser recipient = post.getAuthor();
        AppUser actor = comment.getAuthor();
        if (recipient.getId().equals(actor.getId())) {
            return;
        }

        String title = "내 게시글에 댓글이 달렸습니다.";
        String preview = actor.getNickname() + "님이 \"" + clamp(post.getTitle(), 60) + "\"에 댓글을 남겼습니다.";
        String body = clamp(comment.getBody(), 500);
        saveWithLongTarget(recipient, actor, NotificationType.POST_COMMENT, title, preview, body, "POST", post.getId(), "/" + post.getId());
    }

    @Transactional
    public void createCommentAcceptedNotification(Post post, PostComment comment, AppUser actor) {
        AppUser recipient = comment.getAuthor();
        if (recipient.getId().equals(actor.getId())) {
            return;
        }

        String title = "내 댓글이 채택되었습니다.";
        String preview = "\"" + clamp(post.getTitle(), 60) + "\"에서 내 댓글이 채택되었습니다.";
        String body = clamp(comment.getBody(), 500);
        saveWithLongTarget(recipient, actor, NotificationType.COMMENT_ACCEPTED, title, preview, body, "POST", post.getId(), "/" + post.getId());
    }

    @Transactional
    public void createFollowingPostNotifications(Post post) {
        if (post == null || "talk".equals(post.getCategory())) {
            return;
        }

        AppUser author = post.getAuthor();
        String title = author.getNickname() + "님의 새 게시글";
        String preview = "\"" + clamp(post.getTitle(), 60) + "\" 게시글이 게시되었습니다.";
        String body = clamp(PostContentText.createExcerpt(post.getContent()), 500);

        for (Follow follow : followRepository.findByFollowee(author)) {
            AppUser recipient = follow.getFollower();
            if (recipient.getId().equals(author.getId())) {
                continue;
            }

            saveWithLongTarget(
                    recipient,
                    author,
                    NotificationType.FOLLOWING_POST,
                    title,
                    preview,
                    body,
                    "POST",
                    post.getId(),
                    "/" + post.getId()
            );
        }
    }

    @Transactional
    public void createAdminNotice(AppUser recipient, String title, String body, String targetUrl) {
        save(
                recipient,
                null,
                NotificationType.ADMIN_NOTICE,
                clamp(defaultText(title, "공지사항이 도착했습니다."), 160),
                clamp(defaultText(body, "관리자가 공지를 보냈습니다."), 240),
                clamp(defaultText(body, "관리자가 공지를 보냈습니다."), 2000),
                "ADMIN_NOTICE",
                null,
                targetUrl
        );
    }

    @Transactional
    public void createReportSubmittedNotification(AppUser recipient, AppUser actor, String reportId, String targetLabel) {
        String label = defaultText(targetLabel, "신고");
        save(
                recipient,
                actor,
                NotificationType.REPORT_SUBMITTED,
                "신고가 접수되었습니다.",
                "\"" + clamp(label, 80) + "\" 신고가 접수되었습니다.",
                "신고가 정상적으로 접수되었고 확인 대기 상태입니다.",
                "REPORT",
                reportId,
                null
        );
    }

    @Transactional
    public void createReportReviewedNotification(AppUser recipient, AppUser actor, String reportId, String status) {
        String safeStatus = defaultText(status, "확인됨");
        save(
                recipient,
                actor,
                NotificationType.REPORT_REVIEWED,
                "신고가 확인되었습니다.",
                "접수된 신고가 " + safeStatus + " 상태로 변경되었습니다.",
                "관리자가 신고 내용을 확인했습니다. 처리 상태: " + safeStatus,
                "REPORT",
                reportId,
                null
        );
    }

    private void saveWithLongTarget(
            AppUser recipient,
            AppUser actor,
            NotificationType type,
            String title,
            String preview,
            String body,
            String targetType,
            Long targetId,
            String targetUrl
    ) {
        save(recipient, actor, type, title, preview, body, targetType, targetId == null ? null : String.valueOf(targetId), targetUrl);
    }

    private void save(
            AppUser recipient,
            AppUser actor,
            NotificationType type,
            String title,
            String preview,
            String body,
            String targetType,
            String targetId,
            String targetUrl
    ) {
        if (!isNotificationEnabled(recipient, type)) {
            return;
        }

        notificationRepository.save(new Notification(
                recipient,
                actor,
                type,
                clamp(title, 160),
                clamp(preview, 240),
                clamp(body, 2000),
                targetType,
                clampNullable(targetId, 80),
                clampNullable(targetUrl, 240)
        ));
    }

    private boolean isNotificationEnabled(AppUser recipient, NotificationType type) {
        if (type == NotificationType.ADMIN_NOTICE) {
            return true;
        }
        return notificationPreferenceRepository.findByUserAndType(recipient, type)
                .map(NotificationPreference::isEnabled)
                .orElse(true);
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String clamp(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() > max ? normalized.substring(0, max) : normalized;
    }

    private static String clampNullable(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return clamp(value, max);
    }
}
