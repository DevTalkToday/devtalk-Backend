package com.example.demo.profile;

import com.example.demo.auth.AppUser;
import com.example.demo.auth.UserRepository;
import com.example.demo.auth.dto.UserResponse;
import com.example.demo.post.BugResponse;
import com.example.demo.post.Post;
import com.example.demo.post.PostBookmark;
import com.example.demo.post.PostBookmarkRepository;
import com.example.demo.post.PostAuthorResponse;
import com.example.demo.post.PostComment;
import com.example.demo.post.PostCommentRepository;
import com.example.demo.post.PostListResponse;
import com.example.demo.post.PostRepository;
import com.example.demo.post.PostSummaryResponse;
import com.example.demo.post.QuestionResponse;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ProfileService {
    private static final int MAX_PROFILE_ITEMS = 48;
    private static final String PRIVATE_POST_CATEGORY = "talk";

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostCommentRepository commentRepository;
    private final PostBookmarkRepository bookmarkRepository;

    public ProfileService(
            UserRepository userRepository,
            PostRepository postRepository,
            PostCommentRepository commentRepository,
            PostBookmarkRepository bookmarkRepository
    ) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.bookmarkRepository = bookmarkRepository;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getMe(AppUser currentUser) {
        AppUser user = findUser(currentUser);
        return new ProfileResponse(
                UserResponse.from(user),
                postRepository.countByAuthor(user),
                commentRepository.countByAuthor(user),
                commentRepository.countAcceptedByAuthor(user)
        );
    }

    @Transactional(readOnly = true)
    public PublicProfileResponse getPublicProfile(Long userId) {
        AppUser user = findPublicUser(userId);
        return new PublicProfileResponse(
                PublicProfileUserResponse.from(user),
                postRepository.countByAuthorAndCategoryNot(user, PRIVATE_POST_CATEGORY),
                commentRepository.countByAuthorAndPostCategoryNot(user, PRIVATE_POST_CATEGORY),
                commentRepository.countAcceptedByAuthor(user)
        );
    }

    @Transactional
    public UserResponse update(AppUser currentUser, ProfileUpdatePayload payload) {
        AppUser user = findUser(currentUser);
        user.updateProfile(
                normalizeNickname(payload.nickname()),
                normalizeDescription(payload.description()),
                normalizeMajors(payload.majors())
        );
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateAvatar(AppUser currentUser, ProfileAvatarPayload payload) {
        AppUser user = findUser(currentUser);
        user.updateAvatarUrl(normalizeAvatarUrl(payload == null ? null : payload.avatarUrl()));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public PostListResponse listPosts(AppUser currentUser, int page, int limit) {
        AppUser user = findUser(currentUser);
        return listPostsForUser(user, currentUser, page, limit);
    }

    @Transactional(readOnly = true)
    public PostListResponse listPosts(Long userId, int page, int limit) {
        AppUser user = findPublicUser(userId);
        return listPublicPostsForUser(user, page, limit);
    }

    @Transactional(readOnly = true)
    public PostListResponse listBookmarks(AppUser currentUser, int page, int limit) {
        AppUser user = findUser(currentUser);
        int safeLimit = safeLimit(limit);
        var pageResult = bookmarkRepository.findReadableByUser(
                user,
                PageRequest.of(Math.max(page - 1, 0), safeLimit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
        );
        int totalPages = Math.max(pageResult.getTotalPages(), 1);
        int safePage = safePage(page, totalPages);

        if (safePage != pageResult.getNumber() + 1 && pageResult.getTotalElements() > 0) {
            pageResult = bookmarkRepository.findReadableByUser(
                    user,
                    PageRequest.of(safePage - 1, safeLimit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
            );
        }

        List<PostSummaryResponse> items = pageResult.getContent().stream()
                .map(PostBookmark::getPost)
                .map(post -> toPostSummaryResponse(post, true))
                .toList();

        return new PostListResponse(items, new PostListResponse.PageInfo(
                safePage,
                safeLimit,
                Math.toIntExact(pageResult.getTotalElements()),
                totalPages,
                safePage < totalPages,
                safePage > 1
        ));
    }

    private PostListResponse listPostsForUser(AppUser user, AppUser viewer, int page, int limit) {
        int safeLimit = safeLimit(limit);
        long totalCount = postRepository.countByAuthor(user);
        int totalPages = totalPages(totalCount, safeLimit);
        int safePage = safePage(page, totalPages);

        List<Post> posts = postRepository.findByAuthorOrderByCreatedAtDesc(user, PageRequest.of(safePage - 1, safeLimit));
        List<PostSummaryResponse> items = toPostSummaryResponses(posts, viewer);

        return new PostListResponse(items, new PostListResponse.PageInfo(
                safePage,
                safeLimit,
                Math.toIntExact(totalCount),
                totalPages,
                safePage < totalPages,
                safePage > 1
        ));
    }

    private PostListResponse listPublicPostsForUser(AppUser user, int page, int limit) {
        int safeLimit = safeLimit(limit);
        long totalCount = postRepository.countByAuthorAndCategoryNot(user, PRIVATE_POST_CATEGORY);
        int totalPages = totalPages(totalCount, safeLimit);
        int safePage = safePage(page, totalPages);

        List<PostSummaryResponse> items = postRepository
                .findByAuthorAndCategoryNotOrderByCreatedAtDesc(user, PRIVATE_POST_CATEGORY, PageRequest.of(safePage - 1, safeLimit))
                .stream()
                .map(post -> toPostSummaryResponse(post, false))
                .toList();

        return new PostListResponse(items, new PostListResponse.PageInfo(
                safePage,
                safeLimit,
                Math.toIntExact(totalCount),
                totalPages,
                safePage < totalPages,
                safePage > 1
        ));
    }

    @Transactional(readOnly = true)
    public ProfileCommentListResponse listComments(AppUser currentUser, int page, int limit) {
        AppUser user = findUser(currentUser);
        return listCommentsForUser(user, page, limit);
    }

    @Transactional(readOnly = true)
    public ProfileCommentListResponse listComments(Long userId, int page, int limit) {
        AppUser user = findPublicUser(userId);
        return listPublicCommentsForUser(user, page, limit);
    }

    private ProfileCommentListResponse listCommentsForUser(AppUser user, int page, int limit) {
        int safeLimit = safeLimit(limit);
        long totalCount = commentRepository.countByAuthor(user);
        int totalPages = totalPages(totalCount, safeLimit);
        int safePage = safePage(page, totalPages);

        List<ProfileCommentResponse> items = commentRepository
                .findByAuthorOrderByCreatedAtDesc(user, PageRequest.of(safePage - 1, safeLimit))
                .stream()
                .map(this::toProfileCommentResponse)
                .toList();

        return new ProfileCommentListResponse(items, new ProfileCommentListResponse.PageInfo(
                safePage,
                safeLimit,
                Math.toIntExact(totalCount),
                totalPages,
                safePage < totalPages,
                safePage > 1
        ));
    }

    private ProfileCommentListResponse listPublicCommentsForUser(AppUser user, int page, int limit) {
        int safeLimit = safeLimit(limit);
        long totalCount = commentRepository.countByAuthorAndPostCategoryNot(user, PRIVATE_POST_CATEGORY);
        int totalPages = totalPages(totalCount, safeLimit);
        int safePage = safePage(page, totalPages);

        List<ProfileCommentResponse> items = commentRepository
                .findByAuthorAndPostCategoryNotOrderByCreatedAtDesc(user, PRIVATE_POST_CATEGORY, PageRequest.of(safePage - 1, safeLimit))
                .stream()
                .map(this::toProfileCommentResponse)
                .toList();

        return new ProfileCommentListResponse(items, new ProfileCommentListResponse.PageInfo(
                safePage,
                safeLimit,
                Math.toIntExact(totalCount),
                totalPages,
                safePage < totalPages,
                safePage > 1
        ));
    }

    private AppUser findUser(AppUser currentUser) {
        return userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login is required"));
    }

    private AppUser findPublicUser(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        if (!user.isProfileCompleted() || "__guest__".equalsIgnoreCase(user.getUsername())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
        }

        return user;
    }

    private ProfileCommentResponse toProfileCommentResponse(PostComment comment) {
        Post post = comment.getPost();
        return new ProfileCommentResponse(
                String.valueOf(comment.getId()),
                String.valueOf(post.getId()),
                post.getTitle(),
                post.getCategory(),
                "/" + post.getId(),
                comment.getBody(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                comment.getLikeCount(),
                post.getAcceptedCommentId() != null && post.getAcceptedCommentId().equals(comment.getId())
        );
    }

    private PostSummaryResponse toPostSummaryResponse(Post post, boolean bookmarked) {
        return new PostSummaryResponse(
                String.valueOf(post.getId()),
                post.getTitle(),
                createExcerpt(post.getContent()),
                post.getCategory(),
                toAuthorResponse(post.getAuthor()),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getComments().size(),
                post.getLikeCount(),
                post.getBookmarkCount(),
                bookmarked,
                post.getViewCount(),
                List.copyOf(post.getTags()),
                List.copyOf(post.getMajors()),
                toQuestionResponse(post),
                toBugResponse(post)
        );
    }

    private PostAuthorResponse toAuthorResponse(AppUser user) {
        String role = user.getMajors().isEmpty() ? "Member" : String.join(", ", user.getMajors());
        return new PostAuthorResponse(String.valueOf(user.getId()), user.getNickname(), role, user.getAvatarUrl());
    }

    private QuestionResponse toQuestionResponse(Post post) {
        if (!"qna".equals(post.getCategory())) return null;
        return new QuestionResponse(
                true,
                nullToBlank(post.getQuestionExpected()),
                nullToBlank(post.getQuestionActual()),
                List.copyOf(post.getQuestionReproductionSteps()),
                post.getAcceptedCommentId() == null ? null : String.valueOf(post.getAcceptedCommentId())
        );
    }

    private BugResponse toBugResponse(Post post) {
        if (!"bug".equals(post.getCategory())) return null;
        return new BugResponse(
                nullToDefault(post.getBugStatus(), "open"),
                nullToBlank(post.getBugExpected()),
                nullToBlank(post.getBugActual()),
                List.copyOf(post.getBugReproductionSteps()),
                post.getBugWatchers(),
                post.getAcceptedCommentId() == null ? null : String.valueOf(post.getAcceptedCommentId())
        );
    }

    private static String normalizeNickname(String value) {
        String normalized = trim(value);
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "NICKNAME_REQUIRED");
        }
        return clamp(normalized, 120);
    }

    private static String normalizeDescription(String value) {
        String normalized = trim(value);
        return normalized.isBlank() ? null : clamp(normalized, 500);
    }

    private static List<String> normalizeMajors(List<String> majors) {
        if (majors == null) return List.of();
        return majors.stream()
                .map(ProfileService::trim)
                .filter(value -> !value.isBlank())
                .map(value -> clamp(value, 80))
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(8)
                .toList();
    }

    private static String normalizeAvatarUrl(String value) {
        String normalized = trim(value);
        if (normalized.isBlank()) return null;

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (
                !lower.startsWith("data:image/")
                        && !lower.startsWith("http://")
                        && !lower.startsWith("https://")
                        && !lower.startsWith("/")
        ) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_AVATAR_URL");
        }
        return normalized;
    }

    private static int safeLimit(int limit) {
        return Math.min(Math.max(limit, 1), MAX_PROFILE_ITEMS);
    }

    private static int totalPages(long totalCount, int limit) {
        return Math.max((int) Math.ceil(totalCount / (double) limit), 1);
    }

    private static int safePage(int page, int totalPages) {
        return Math.min(Math.max(page, 1), totalPages);
    }

    private static String createExcerpt(String content) {
        String normalized = trim(content).replaceAll("\\s+", " ");
        return normalized.length() > 150 ? normalized.substring(0, 150) : normalized;
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static String nullToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<PostSummaryResponse> toPostSummaryResponses(List<Post> posts, AppUser viewer) {
        Set<Long> bookmarkedIds = bookmarkedPostIds(viewer, posts);
        return posts.stream()
                .map(post -> toPostSummaryResponse(post, bookmarkedIds.contains(post.getId())))
                .toList();
    }

    private Set<Long> bookmarkedPostIds(AppUser viewer, List<Post> posts) {
        if (viewer == null || posts.isEmpty()) return Set.of();
        return Set.copyOf(bookmarkRepository.findBookmarkedPostIds(
                viewer,
                posts.stream().map(Post::getId).toList()
        ));
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String clamp(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
