package com.example.demo.post;

import com.example.demo.auth.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "post_comment_likes",
        uniqueConstraints = @UniqueConstraint(name = "uk_post_comment_likes_user_comment", columnNames = {"user_id", "comment_id"}),
        indexes = {
                @Index(name = "idx_post_comment_likes_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_post_comment_likes_comment", columnList = "comment_id")
        }
)
public class PostCommentLike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private PostComment comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PostCommentLike() {
    }

    public PostCommentLike(AppUser user, PostComment comment) {
        this.user = user;
        this.comment = comment;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public PostComment getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
