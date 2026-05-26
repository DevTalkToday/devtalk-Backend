package com.example.demo.post;

import com.example.demo.auth.AppUser;
import com.example.demo.notification.NotificationService;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PostService {
    private static final Set<String> CATEGORIES = Set.of("qna", "bug", "talk");
    private static final Set<String> SORTS = Set.of("latest", "oldest", "popular", "views", "comments");
    private static final Set<String> RESOLUTIONS = Set.of("all", "resolved", "unresolved");
    private static final Set<String> BUG_STATUSES = Set.of("open", "investigating", "fixed", "closed");
    private static final Set<String> BUG_PRIORITIES = Set.of("P0", "P1", "P2", "P3");

    private final PostRepository postRepository;
    private final PostCommentRepository commentRepository;
    private final NotificationService notificationService;

    public PostService(
            PostRepository postRepository,
            PostCommentRepository commentRepository,
            NotificationService notificationService
    ) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.notificationService = notificationService;
    }

    public PostListResponse listPosts(
            String category,
            String q,
            String sort,
            String resolution,
            String match,
            int page,
            int limit
    ) {
        List<String> categories = parseCsv(category).stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(CATEGORIES::contains)
                .toList();
        List<String> resolutions = parseCsv(resolution == null ? "all" : resolution).stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(RESOLUTIONS::contains)
                .toList();
        String safeSort = SORTS.contains(normalizeLower(sort)) ? normalizeLower(sort) : "latest";
        String safeMatch = "or".equals(normalizeLower(match)) ? "or" : "and";
        List<String> keywords = parseKeywords(q);

        List<Post> filtered = postRepository.findAll().stream()
                .filter(post -> matchesCategoryAndResolution(post, categories, resolutions, safeMatch))
                .filter(post -> matchesKeywords(post, keywords, safeMatch))
                .sorted(comparatorFor(safeSort))
                .toList();

        int safeLimit = Math.min(Math.max(limit, 1), 24);
        int totalCount = filtered.size();
        int totalPages = Math.max((int) Math.ceil(totalCount / (double) safeLimit), 1);
        int safePage = Math.min(Math.max(page, 1), totalPages);
        int start = Math.min((safePage - 1) * safeLimit, totalCount);
        int end = Math.min(start + safeLimit, totalCount);

        return new PostListResponse(
                filtered.subList(start, end).stream().map(this::toSummaryResponse).toList(),
                new PostListResponse.PageInfo(
                        safePage,
                        safeLimit,
                        totalCount,
                        totalPages,
                        safePage < totalPages,
                        safePage > 1
                )
        );
    }

    @Transactional
    public PostResponse getPost(Long id, boolean track) {
        Post post = findPost(id);
        if (track) post.incrementViewCount();
        return toResponse(post);
    }

    @Transactional
    public PostResponse createPost(PostPayload request, AppUser author) {
        PostPayload payload = normalizePostPayload(request, null);
        Post post = new Post(payload.title(), payload.content(), payload.category(), author);
        post.apply(payload);
        return toResponse(postRepository.save(post));
    }

    @Transactional
    public PostResponse updatePost(Long id, PostPayload request) {
        Post post = findPost(id);
        post.apply(normalizePostPayload(request, post));
        return toResponse(post);
    }

    @Transactional
    public void deletePost(Long id) {
        if (!postRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        postRepository.deleteById(id);
    }

    @Transactional
    public PostResponse createComment(Long postId, CommentPayload request, AppUser author) {
        Post post = findPost(postId);
        String body = normalizeCommentBody(request == null ? null : request.body());
        PostComment comment = new PostComment(post, author, body);
        post.addComment(comment);
        commentRepository.save(comment);
        notificationService.createPostCommentNotification(post, comment);
        return toResponse(post);
    }

    @Transactional
    public PostResponse updateComment(Long postId, Long commentId, CommentPayload request) {
        PostComment comment = findComment(postId, commentId);
        comment.updateBody(normalizeCommentBody(request == null ? null : request.body()));
        return toResponse(findPost(postId));
    }

    @Transactional
    public PostResponse deleteComment(Long postId, Long commentId) {
        Post post = findPost(postId);
        PostComment comment = findComment(postId, commentId);
        post.removeComment(comment);
        if (commentId.equals(post.getAcceptedCommentId())) {
            post.setAcceptedCommentId(null);
        }
        return toResponse(post);
    }

    @Transactional
    public PostResponse setAcceptedComment(Long postId, Long commentId, CommentAcceptPayload request, AppUser actor) {
        Post post = findPost(postId);
        if ("talk".equals(post.getCategory())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMMENT_ACCEPT_ONLY_FOR_QNA_OR_BUG");
        }
        PostComment comment = findComment(postId, commentId);
        if (request == null || request.accepted() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "COMMENT_ACCEPTED_REQUIRED");
        }

        if (request.accepted()) {
            post.setAcceptedCommentId(commentId);
            notificationService.createCommentAcceptedNotification(post, comment, actor);
        } else {
            post.setAcceptedCommentId(null);
        }
        return toResponse(post);
    }

    private Post findPost(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private PostComment findComment(Long postId, Long commentId) {
        return commentRepository.findByIdAndPostId(commentId, postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND"));
    }

    private PostPayload normalizePostPayload(PostPayload request, Post existing) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "TITLE_AND_CONTENT_REQUIRED");
        }

        String title = clamp(request == null ? null : request.title(), 120);
        String content = trim(request == null ? null : request.content());
        String category = normalizeLower(request == null ? null : request.category());
        if (!CATEGORIES.contains(category)) category = existing == null ? "talk" : existing.getCategory();

        if (title.isBlank() || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "TITLE_AND_CONTENT_REQUIRED");
        }

        PostPayload.QuestionPayload question = null;
        PostPayload.BugPayload bug = null;

        if ("qna".equals(category)) {
            PostPayload.QuestionPayload source = request.question();
            question = new PostPayload.QuestionPayload(
                    source != null && source.solved(),
                    clamp(source == null ? null : source.environment(), 180),
                    clamp(source == null ? null : source.tried(), 180),
                    source == null ? null : source.acceptedCommentId()
            );
        }

        if ("bug".equals(category)) {
            PostPayload.BugPayload source = request.bug();
            String status = normalizeLower(source == null ? null : source.status());
            String priority = trim(source == null ? null : source.priority()).toUpperCase(Locale.ROOT);
            bug = new PostPayload.BugPayload(
                    BUG_STATUSES.contains(status) ? status : "open",
                    BUG_PRIORITIES.contains(priority) ? priority : "P2",
                    clamp(source == null ? null : source.assignee(), 80),
                    clamp(source == null ? null : source.environment(), 180),
                    clamp(source == null ? null : source.expected(), 240),
                    clamp(source == null ? null : source.actual(), 240),
                    cleanList(source == null ? null : source.reproductionSteps(), 8),
                    cleanList(source == null ? null : source.labels(), 6),
                    source == null ? 0 : Math.max(source.watchers(), 0),
                    source == null ? null : source.acceptedCommentId()
            );
        }

        return new PostPayload(
                title,
                content,
                category,
                cleanList(request.tags(), 8),
                cleanList(request.majors(), 5),
                question,
                bug
        );
    }

    private String normalizeCommentBody(String body) {
        String normalized = trim(body);
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "COMMENT_BODY_REQUIRED");
        }
        return normalized.length() > 1200 ? normalized.substring(0, 1200) : normalized;
    }

    private boolean matchesCategoryAndResolution(Post post, List<String> categories, List<String> resolutions, String match) {
        boolean hasCategoryFilter = !categories.isEmpty();
        boolean hasResolutionFilter = !resolutions.isEmpty() && !resolutions.contains("all");
        boolean categoryMatch = !hasCategoryFilter || categories.contains(post.getCategory());
        boolean resolutionMatch = !hasResolutionFilter || resolutions.stream().anyMatch(value -> matchesResolution(post, value));

        if ("or".equals(match) && (hasCategoryFilter || hasResolutionFilter)) {
            return (hasCategoryFilter && categoryMatch) || (hasResolutionFilter && resolutionMatch);
        }
        return categoryMatch && resolutionMatch;
    }

    private boolean matchesResolution(Post post, String resolution) {
        Boolean resolved = isResolved(post);
        if (resolved == null) return false;
        return "resolved".equals(resolution) ? resolved : !resolved;
    }

    private Boolean isResolved(Post post) {
        if ("qna".equals(post.getCategory())) return post.isQuestionSolved();
        if ("bug".equals(post.getCategory())) return "fixed".equals(post.getBugStatus()) || "closed".equals(post.getBugStatus());
        return null;
    }

    private boolean matchesKeywords(Post post, List<String> keywords, String match) {
        if (keywords.isEmpty()) return true;
        String haystack = String.join(" ", List.of(
                post.getTitle(),
                createExcerpt(post.getContent()),
                post.getContent(),
                String.join(" ", post.getTags()),
                String.join(" ", post.getMajors()),
                nullToBlank(post.getQuestionEnvironment()),
                nullToBlank(post.getQuestionTried()),
                nullToBlank(post.getBugEnvironment()),
                nullToBlank(post.getBugExpected()),
                nullToBlank(post.getBugActual()),
                String.join(" ", post.getBugLabels()),
                post.getComments().stream().map(PostComment::getBody).reduce("", (left, right) -> left + " " + right)
        )).toLowerCase(Locale.ROOT);

        return "or".equals(match)
                ? keywords.stream().anyMatch(haystack::contains)
                : keywords.stream().allMatch(haystack::contains);
    }

    private Comparator<Post> comparatorFor(String sort) {
        Comparator<Post> latest = Comparator
                .comparing(Post::getCreatedAt, Comparator.reverseOrder())
                .thenComparing(Post::getUpdatedAt, Comparator.reverseOrder());
        if ("oldest".equals(sort)) return Comparator.comparing(Post::getCreatedAt).thenComparing(Post::getUpdatedAt);
        if ("popular".equals(sort)) return Comparator.comparingInt(Post::getLikeCount).reversed().thenComparing(latest);
        if ("views".equals(sort)) return Comparator.comparingInt(Post::getViewCount).reversed().thenComparing(latest);
        if ("comments".equals(sort)) return Comparator.comparingInt((Post post) -> post.getComments().size()).reversed().thenComparing(latest);
        return latest;
    }

    private PostResponse toResponse(Post post) {
        return new PostResponse(
                String.valueOf(post.getId()),
                post.getTitle(),
                createExcerpt(post.getContent()),
                post.getContent(),
                post.getCategory(),
                toAuthorResponse(post.getAuthor()),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getComments().size(),
                post.getLikeCount(),
                post.getBookmarkCount(),
                post.getViewCount(),
                List.copyOf(post.getTags()),
                List.copyOf(post.getMajors()),
                post.getComments().stream().map(comment -> toCommentResponse(comment, post.getAcceptedCommentId())).toList(),
                toQuestionResponse(post),
                toBugResponse(post)
        );
    }

    private PostSummaryResponse toSummaryResponse(Post post) {
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
                post.getViewCount(),
                List.copyOf(post.getTags()),
                List.copyOf(post.getMajors()),
                toQuestionResponse(post),
                toBugResponse(post)
        );
    }

    private PostCommentResponse toCommentResponse(PostComment comment, Long acceptedCommentId) {
        return new PostCommentResponse(
                String.valueOf(comment.getId()),
                toAuthorResponse(comment.getAuthor()),
                comment.getBody(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                comment.getLikeCount(),
                acceptedCommentId != null && acceptedCommentId.equals(comment.getId())
        );
    }

    private PostAuthorResponse toAuthorResponse(AppUser user) {
        String role = user.getMajors().isEmpty() ? "Member" : String.join(", ", user.getMajors());
        return new PostAuthorResponse(String.valueOf(user.getId()), user.getNickname(), role, user.getAvatarUrl());
    }

    private QuestionResponse toQuestionResponse(Post post) {
        if (!"qna".equals(post.getCategory())) return null;
        return new QuestionResponse(
                post.isQuestionSolved(),
                nullToBlank(post.getQuestionEnvironment()),
                nullToBlank(post.getQuestionTried()),
                post.getAcceptedCommentId() == null ? null : String.valueOf(post.getAcceptedCommentId())
        );
    }

    private BugResponse toBugResponse(Post post) {
        if (!"bug".equals(post.getCategory())) return null;
        return new BugResponse(
                nullToDefault(post.getBugStatus(), "open"),
                nullToDefault(post.getBugPriority(), "P2"),
                nullToBlank(post.getBugAssignee()),
                nullToBlank(post.getBugEnvironment()),
                nullToBlank(post.getBugExpected()),
                nullToBlank(post.getBugActual()),
                List.copyOf(post.getBugReproductionSteps()),
                List.copyOf(post.getBugLabels()),
                post.getBugWatchers(),
                post.getAcceptedCommentId() == null ? null : String.valueOf(post.getAcceptedCommentId())
        );
    }

    private static List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> items = new ArrayList<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isBlank()) items.add(trimmed);
        }
        return items;
    }

    private static List<String> parseKeywords(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> items = new ArrayList<>();
        for (String item : value.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!item.isBlank()) items.add(item);
        }
        return items;
    }

    private static List<String> cleanList(List<String> values, int maxItems) {
        if (values == null) return List.of();
        return values.stream()
                .map(PostService::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(maxItems)
                .toList();
    }

    private static String createExcerpt(String content) {
        String normalized = trim(content).replaceAll("\\s+", " ");
        return normalized.length() > 150 ? normalized.substring(0, 150) : normalized;
    }

    private static String normalizeLower(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String clamp(String value, int max) {
        String normalized = trim(value);
        return normalized.length() > max ? normalized.substring(0, max) : normalized;
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static String nullToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
