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
        name = "post_views",
        uniqueConstraints = @UniqueConstraint(name = "uk_post_views_user_post", columnNames = {"user_id", "post_id"}),
        indexes = {
                @Index(name = "idx_post_views_user_viewed", columnList = "user_id, viewed_at"),
                @Index(name = "idx_post_views_post", columnList = "post_id")
        }
)
public class PostView {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt = Instant.now();

    protected PostView() {
    }

    public PostView(AppUser user, Post post, Instant viewedAt) {
        this.user = user;
        this.post = post;
        this.viewedAt = viewedAt == null ? Instant.now() : viewedAt;
    }

    public Instant getViewedAt() {
        return viewedAt;
    }

    public void updateViewedAt(Instant viewedAt) {
        this.viewedAt = viewedAt == null ? Instant.now() : viewedAt;
    }
}
