package com.example.demo.post;

import com.example.demo.auth.AppUser;
import com.example.demo.auth.AuthService;
import com.example.demo.auth.AuthToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/posts")
public class PostController {
    private final PostService postService;
    private final AuthService authService;

    public PostController(PostService postService, AuthService authService) {
        this.postService = postService;
        this.authService = authService;
    }

    @GetMapping
    public PostListResponse listPosts(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "sort", defaultValue = "latest") String sort,
            @RequestParam(name = "resolution", defaultValue = "all") String resolution,
            @RequestParam(name = "match", defaultValue = "and") String match,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "limit", defaultValue = "6") int limit
    ) {
        return postService.listPosts(category, q, sort, resolution, match, page, limit, resolveViewer(authorization));
    }

    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestBody PostPayload request
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(request, user));
    }

    @GetMapping("/{id}")
    public PostResponse getPost(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestParam(name = "track", defaultValue = "true") boolean track
    ) {
        return postService.getPost(id, track, resolveViewer(authorization));
    }

    @PutMapping("/{id}")
    public PostResponse updatePost(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody PostPayload request
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return postService.updatePost(id, request, user);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        postService.deletePost(id, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/bookmark")
    public PostResponse bookmarkPost(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return postService.bookmarkPost(id, user);
    }

    @DeleteMapping("/{id}/bookmark")
    public PostResponse unbookmarkPost(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return postService.unbookmarkPost(id, user);
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<PostResponse> createComment(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody CommentPayload request
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createComment(id, request, user));
    }

    @PutMapping("/{id}/comments/{commentId}")
    public PostResponse updateComment(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @PathVariable Long commentId,
            @RequestBody CommentPayload request
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return postService.updateComment(id, commentId, request, user);
    }

    @PatchMapping("/{id}/comments/{commentId}")
    public PostResponse acceptComment(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @PathVariable Long commentId,
            @RequestBody CommentAcceptPayload request
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return postService.setAcceptedComment(id, commentId, request, user);
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public PostResponse deleteComment(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @PathVariable Long commentId
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return postService.deleteComment(id, commentId, user);
    }

    private AppUser resolveViewer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }

        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) return null;

        try {
            AuthToken authToken = authService.authenticateToken(token);
            return authToken.isGuest() ? null : authToken.getUser();
        } catch (ResponseStatusException ignored) {
            return null;
        }
    }

    private static String readBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is required");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is required");
        }
        return token;
    }
}
