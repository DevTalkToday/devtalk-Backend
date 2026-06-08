package com.example.demo.post;

import com.example.demo.auth.AdminAccess;
import com.example.demo.auth.AppUser;
import com.example.demo.notification.NotificationService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class PostService {
    private static final String DEVTALK_BOT_USERNAME = "seed_writer";
    private static final String DEVTALK_BOT_NICKNAME = "Devtalk";
    private static final String DEVTALK_MANAGER_EMAIL = "s25002@gsm.hs.kr";
    private static final String EMPTY_CATEGORY_SENTINEL = "__none__";
    private static final String RESOLUTION_MODE_ALL = "ALL";
    private static final String RESOLUTION_MODE_RESOLVED = "RESOLVED";
    private static final String RESOLUTION_MODE_UNRESOLVED = "UNRESOLVED";
    private static final String RESOLUTION_MODE_ANY_STATUS = "ANY_STATUS";
    private static final String PRIVATE_POST_CATEGORY = "talk";
    private static final Set<String> CATEGORIES = Set.of("qna", "bug", "talk");
    private static final Set<String> SORTS = Set.of("latest", "oldest", "popular", "views", "comments");
    private static final Set<String> RESOLUTIONS = Set.of("all", "resolved", "unresolved");
    private static final Set<String> BUG_STATUSES = Set.of("open", "investigating", "fixed", "closed");

    private final PostRepository postRepository;
    private final PostCommentRepository commentRepository;
    private final PostBookmarkRepository bookmarkRepository;
    private final NotificationService notificationService;

    public PostService(
            PostRepository postRepository,
            PostCommentRepository commentRepository,
            PostBookmarkRepository bookmarkRepository,
            NotificationService notificationService
    ) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.notificationService = notificationService;
    }

    public PostListResponse listPosts(
            String category,
            String q,
            String sort,
            String resolution,
            String match,
            int page,
            int limit,
            AppUser viewer
    ) {
        List<String> categories = parseCsv(category).stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(CATEGORIES::contains)
                .toList();
        List<String> resolutions = normalizeResolutions(parseCsv(resolution == null ? "all" : resolution).stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(RESOLUTIONS::contains)
                .toList());
        String safeSort = SORTS.contains(normalizeLower(sort)) ? normalizeLower(sort) : "latest";
        String safeMatch = "or".equals(normalizeLower(match)) ? "or" : "and";
        List<String> keywords = parseKeywords(q);
        int safeLimit = Math.min(Math.max(limit, 1), 24);
        Long viewerId = viewerId(viewer);

        if (canUseDatabaseListing(categories, resolutions, keywords, safeSort, safeMatch)) {
            return listPostsFromRepository(categories, resolutions, safeSort, page, safeLimit, viewer);
        }

        List<Post> filtered = loadPostsForInMemoryFiltering(categories, resolutions, safeMatch, viewerId).stream()
                .filter(post -> matchesCategoryAndResolution(post, categories, resolutions, safeMatch))
                .filter(post -> matchesKeywords(post, keywords, safeMatch))
                .sorted(comparatorFor(safeSort))
                .toList();

        int totalCount = filtered.size();
        int totalPages = Math.max((int) Math.ceil(totalCount / (double) safeLimit), 1);
        int safePage = Math.min(Math.max(page, 1), totalPages);
        int start = Math.min((safePage - 1) * safeLimit, totalCount);
        int end = Math.min(start + safeLimit, totalCount);

        return new PostListResponse(
                toSummaryResponses(filtered.subList(start, end), viewer),
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
    public PostResponse getPost(Long id, boolean track, AppUser viewer) {
        Post post = findPost(id);
        requireReadablePost(post, viewer);
        if (track) post.incrementViewCount();
        return toResponse(post, viewer);
    }

    @Transactional
    public PostResponse createPost(PostPayload request, AppUser author) {
        PostPayload payload = normalizePostPayload(request, null);
        Post post = new Post(payload.title(), payload.content(), payload.category(), author);
        post.apply(payload);
        return toResponse(postRepository.save(post), author);
    }

    @Transactional
    public PostResponse updatePost(Long id, PostPayload request, AppUser actor) {
        Post post = findPost(id);
        requirePostManager(post, actor);
        post.apply(normalizePostPayload(request, post));
        return toResponse(post, actor);
    }

    @Transactional
    public void deletePost(Long id, AppUser actor) {
        Post post = findPost(id);
        requirePostDeleter(post, actor);
        bookmarkRepository.deleteByPost(post);
        postRepository.delete(post);
    }

    @Transactional
    public PostResponse bookmarkPost(Long id, AppUser actor) {
        Post post = findPost(id);
        requireReadablePost(post, actor);

        if (bookmarkRepository.findByPostAndUser(post, actor).isEmpty()) {
            bookmarkRepository.save(new PostBookmark(actor, post));
            post.incrementBookmarkCount();
        }

        return toResponse(post, actor);
    }

    @Transactional
    public PostResponse unbookmarkPost(Long id, AppUser actor) {
        Post post = findPost(id);
        requireReadablePost(post, actor);

        bookmarkRepository.findByPostAndUser(post, actor).ifPresent((bookmark) -> {
            bookmarkRepository.delete(bookmark);
            post.decrementBookmarkCount();
        });

        return toResponse(post, actor);
    }

    @Transactional
    public PostResponse createComment(Long postId, CommentPayload request, AppUser author) {
        Post post = findPost(postId);
        requireReadablePost(post, author);
        String body = normalizeCommentBody(request == null ? null : request.body());
        PostComment comment = new PostComment(post, author, body);
        post.addComment(comment);
        commentRepository.save(comment);
        notificationService.createPostCommentNotification(post, comment);
        return toResponse(post, author);
    }

    @Transactional
    public PostResponse updateComment(Long postId, Long commentId, CommentPayload request, AppUser actor) {
        Post post = findPost(postId);
        requireReadablePost(post, actor);
        PostComment comment = findComment(postId, commentId);
        requireCommentAuthor(comment, actor);
        comment.updateBody(normalizeCommentBody(request == null ? null : request.body()));
        return toResponse(post, actor);
    }

    @Transactional
    public PostResponse deleteComment(Long postId, Long commentId, AppUser actor) {
        Post post = findPost(postId);
        requireReadablePost(post, actor);
        PostComment comment = findComment(postId, commentId);
        if (!canDeleteComment(actor, comment, post)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "COMMENT_DELETE_FORBIDDEN");
        }
        post.removeComment(comment);
        if (commentId.equals(post.getAcceptedCommentId())) {
            post.setAcceptedCommentId(null);
        }
        return toResponse(post, actor);
    }

    @Transactional
    public PostResponse setAcceptedComment(Long postId, Long commentId, CommentAcceptPayload request, AppUser actor) {
        Post post = findPost(postId);
        requireReadablePost(post, actor);
        if ("talk".equals(post.getCategory())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMMENT_ACCEPT_ONLY_FOR_QNA_OR_BUG");
        }
        PostComment comment = findComment(postId, commentId);
        if (request == null || request.accepted() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "COMMENT_ACCEPTED_REQUIRED");
        }
        requirePostManager(post, actor);

        if (request.accepted()) {
            post.setAcceptedCommentId(commentId);
            notificationService.createCommentAcceptedNotification(post, comment, actor);
        } else {
            post.setAcceptedCommentId(null);
        }
        return toResponse(post, actor);
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
                    clamp(source == null ? null : source.expected(), 240),
                    clamp(source == null ? null : source.actual(), 240),
                    cleanList(source == null ? null : source.reproductionSteps(), 8),
                    source == null ? null : source.acceptedCommentId()
            );
        }

        if ("bug".equals(category)) {
            PostPayload.BugPayload source = request.bug();
            String status = normalizeLower(source == null ? null : source.status());
            bug = new PostPayload.BugPayload(
                    BUG_STATUSES.contains(status) ? status : "open",
                    clamp(source == null ? null : source.expected(), 240),
                    clamp(source == null ? null : source.actual(), 240),
                    cleanList(source == null ? null : source.reproductionSteps(), 8),
                    source == null || source.watchers() == null ? 0 : Math.max(source.watchers(), 0),
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
        if ("qna".equals(post.getCategory())) return true;
        if ("bug".equals(post.getCategory())) return "fixed".equals(post.getBugStatus()) || "closed".equals(post.getBugStatus());
        return null;
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

    private PostResponse toResponse(Post post, AppUser viewer) {
        boolean canManagePost = canManagePost(viewer, post);
        boolean canDeletePost = canManagePost || AdminAccess.isAdmin(viewer);
        boolean canAcceptComments = canManagePost && !"talk".equals(post.getCategory());

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
                isBookmarked(post, viewer),
                post.getViewCount(),
                List.copyOf(post.getTags()),
                List.copyOf(post.getMajors()),
                post.getComments().stream().map(comment -> toCommentResponse(comment, post, viewer, canAcceptComments)).toList(),
                toQuestionResponse(post),
                toBugResponse(post),
                canManagePost,
                canDeletePost,
                canAcceptComments
        );
    }

    private PostSummaryResponse toSummaryResponse(Post post, boolean bookmarked) {
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

    private PostCommentResponse toCommentResponse(PostComment comment, Post post, AppUser viewer, boolean canAcceptComments) {
        boolean canEdit = canEditComment(viewer, comment);
        return new PostCommentResponse(
                String.valueOf(comment.getId()),
                toAuthorResponse(comment.getAuthor()),
                comment.getBody(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                comment.getLikeCount(),
                post.getAcceptedCommentId() != null && post.getAcceptedCommentId().equals(comment.getId()),
                canEdit,
                canDeleteComment(viewer, comment, post),
                canAcceptComments
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

    private PostListResponse listPostsFromRepository(
            List<String> categories,
            List<String> resolutions,
            String sort,
            int page,
            int limit,
            AppUser viewer
    ) {
        Long viewerId = viewerId(viewer);
        Page<Post> pageResult = postRepository.findListingPage(
                listingCategories(categories),
                categories.isEmpty(),
                resolutionMode(resolutions),
                viewerId,
                PageRequest.of(Math.max(page - 1, 0), limit, repositorySort(sort))
        );

        int totalPages = Math.max(pageResult.getTotalPages(), 1);
        int safePage = Math.min(Math.max(page, 1), totalPages);
        if (safePage != pageResult.getNumber() + 1 && pageResult.getTotalElements() > 0) {
            pageResult = postRepository.findListingPage(
                    listingCategories(categories),
                    categories.isEmpty(),
                    resolutionMode(resolutions),
                    viewerId,
                    PageRequest.of(safePage - 1, limit, repositorySort(sort))
            );
        }

        return new PostListResponse(
                toSummaryResponses(pageResult.getContent(), viewer),
                new PostListResponse.PageInfo(
                        safePage,
                        limit,
                        Math.toIntExact(pageResult.getTotalElements()),
                        totalPages,
                        safePage < totalPages,
                        safePage > 1
                )
        );
    }

    private List<Post> loadPostsForInMemoryFiltering(List<String> categories, List<String> resolutions, String match, Long viewerId) {
        boolean hasCategoryFilter = !categories.isEmpty();
        boolean hasResolutionFilter = hasActiveResolutionFilter(resolutions);
        if ("or".equals(match) && hasCategoryFilter && hasResolutionFilter) {
            return postRepository.findAllForListing(List.of(EMPTY_CATEGORY_SENTINEL), true, RESOLUTION_MODE_ALL, viewerId);
        }
        return postRepository.findAllForListing(
                listingCategories(categories),
                categories.isEmpty(),
                resolutionMode(resolutions),
                viewerId
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

    private static List<String> normalizeResolutions(List<String> values) {
        if (values.size() <= 1 || !values.contains("all")) {
            return values;
        }
        return values.stream()
                .filter(value -> !"all".equals(value))
                .toList();
    }

    private static String createExcerpt(String content) {
        String normalized = trim(content).replaceAll("\\s+", " ");
        return normalized.length() > 150 ? normalized.substring(0, 150) : normalized;
    }

    private static boolean canUseDatabaseListing(
            List<String> categories,
            List<String> resolutions,
            List<String> keywords,
            String sort,
            String match
    ) {
        if (!keywords.isEmpty()) return false;
        if ("comments".equals(sort)) return false;

        boolean hasCategoryFilter = !categories.isEmpty();
        boolean hasResolutionFilter = hasActiveResolutionFilter(resolutions);
        return !"or".equals(match) || !hasCategoryFilter || !hasResolutionFilter;
    }

    private static boolean hasActiveResolutionFilter(List<String> resolutions) {
        return !resolutions.isEmpty() && !resolutions.contains("all");
    }

    private static List<String> listingCategories(List<String> categories) {
        return categories.isEmpty() ? List.of(EMPTY_CATEGORY_SENTINEL) : categories;
    }

    private static String resolutionMode(List<String> resolutions) {
        boolean resolved = resolutions.contains("resolved");
        boolean unresolved = resolutions.contains("unresolved");
        if (resolved && unresolved) return RESOLUTION_MODE_ANY_STATUS;
        if (resolved) return RESOLUTION_MODE_RESOLVED;
        if (unresolved) return RESOLUTION_MODE_UNRESOLVED;
        return RESOLUTION_MODE_ALL;
    }

    private static Sort repositorySort(String sort) {
        if ("oldest".equals(sort)) {
            return Sort.by(
                    Sort.Order.asc("createdAt"),
                    Sort.Order.asc("updatedAt"),
                    Sort.Order.asc("id")
            );
        }
        if ("popular".equals(sort)) {
            return Sort.by(
                    Sort.Order.desc("likeCount"),
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("updatedAt"),
                    Sort.Order.desc("id")
            );
        }
        if ("views".equals(sort)) {
            return Sort.by(
                    Sort.Order.desc("viewCount"),
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("updatedAt"),
                    Sort.Order.desc("id")
            );
        }
        return Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("updatedAt"),
                Sort.Order.desc("id")
        );
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

    private List<PostSummaryResponse> toSummaryResponses(List<Post> posts, AppUser viewer) {
        Set<Long> bookmarkedIds = bookmarkedPostIds(viewer, posts);
        return posts.stream()
                .map(post -> toSummaryResponse(post, bookmarkedIds.contains(post.getId())))
                .toList();
    }

    private Set<Long> bookmarkedPostIds(AppUser viewer, List<Post> posts) {
        if (viewer == null || posts.isEmpty()) {
            return Set.of();
        }

        List<Long> postIds = posts.stream()
                .map(Post::getId)
                .toList();
        return new HashSet<>(bookmarkRepository.findBookmarkedPostIds(viewer, postIds));
    }

    private boolean isBookmarked(Post post, AppUser viewer) {
        if (viewer == null) return false;
        return bookmarkRepository.existsByPostAndUser(post, viewer);
    }

    private boolean matchesKeywords(Post post, List<String> keywords, String match) {
        if (keywords.isEmpty()) return true;
        String haystack = buildSearchableText(post).toLowerCase(Locale.ROOT);

        return "or".equals(match)
                ? keywords.stream().anyMatch(haystack::contains)
                : keywords.stream().allMatch(haystack::contains);
    }

    private static String buildSearchableText(Post post) {
        StringBuilder builder = new StringBuilder();
        appendSearchValue(builder, post.getTitle());
        appendSearchValue(builder, createExcerpt(post.getContent()));
        appendSearchValue(builder, post.getContent());
        appendSearchValues(builder, post.getTags());
        appendSearchValues(builder, post.getMajors());
        appendSearchValue(builder, post.getQuestionExpected());
        appendSearchValue(builder, post.getQuestionActual());
        appendSearchValues(builder, post.getQuestionReproductionSteps());
        appendSearchValue(builder, post.getBugExpected());
        appendSearchValue(builder, post.getBugActual());
        appendSearchValues(builder, post.getBugReproductionSteps());
        for (PostComment comment : post.getComments()) {
            appendSearchValue(builder, comment.getBody());
        }
        return builder.toString();
    }

    private static void appendSearchValues(StringBuilder builder, List<String> values) {
        for (String value : values) {
            appendSearchValue(builder, value);
        }
    }

    private static void appendSearchValue(StringBuilder builder, String value) {
        String normalized = nullToBlank(value);
        if (normalized.isBlank()) return;
        if (builder.length() > 0) builder.append(' ');
        builder.append(normalized);
    }

    private void requirePostManager(Post post, AppUser actor) {
        if (!canManagePost(actor, post)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "POST_MODIFY_FORBIDDEN");
        }
    }

    private void requireReadablePost(Post post, AppUser viewer) {
        if (!isReadableBy(viewer, post)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
    }

    private void requirePostDeleter(Post post, AppUser actor) {
        if (!canManagePost(actor, post) && !AdminAccess.isAdmin(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "POST_MODIFY_FORBIDDEN");
        }
    }

    private void requireCommentAuthor(PostComment comment, AppUser actor) {
        if (!canEditComment(actor, comment)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "COMMENT_MODIFY_FORBIDDEN");
        }
    }

    private static boolean canManagePost(AppUser actor, Post post) {
        if (actor == null || post == null || post.getAuthor() == null) return false;
        if (post.getAuthor().getId().equals(actor.getId())) return true;
        return isDevtalkBotPost(post) && isDevtalkManager(actor);
    }

    private static boolean isReadableBy(AppUser viewer, Post post) {
        if (post == null) return false;
        if (!isPrivatePost(post)) return true;
        if (viewer == null || post.getAuthor() == null) return false;
        return post.getAuthor().getId().equals(viewer.getId());
    }

    private static boolean isPrivatePost(Post post) {
        return PRIVATE_POST_CATEGORY.equals(post.getCategory());
    }

    private static Long viewerId(AppUser viewer) {
        return viewer == null ? null : viewer.getId();
    }

    private static boolean canEditComment(AppUser actor, PostComment comment) {
        return actor != null
                && comment != null
                && comment.getAuthor() != null
                && comment.getAuthor().getId().equals(actor.getId());
    }

    private static boolean canDeleteComment(AppUser actor, PostComment comment, Post post) {
        return canEditComment(actor, comment) || canManagePost(actor, post) || AdminAccess.isAdmin(actor);
    }

    private static boolean isDevtalkBotPost(Post post) {
        AppUser author = post.getAuthor();
        if (author == null) return false;
        return DEVTALK_BOT_USERNAME.equalsIgnoreCase(nullToBlank(author.getUsername()))
                || DEVTALK_BOT_NICKNAME.equalsIgnoreCase(nullToBlank(author.getNickname()));
    }

    private static boolean isDevtalkManager(AppUser actor) {
        return DEVTALK_MANAGER_EMAIL.equalsIgnoreCase(nullToBlank(actor.getUsername()))
                || DEVTALK_MANAGER_EMAIL.equalsIgnoreCase(nullToBlank(actor.getEmail()));
    }
}
