package com.example.demo.follow;

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
        name = "follows",
        indexes = {
                @Index(name = "idx_follows_follower_created", columnList = "follower_id, created_at"),
                @Index(name = "idx_follows_followee_created", columnList = "followee_id, created_at")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_follows_pair", columnNames = {"follower_id", "followee_id"})
)
public class Follow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id", nullable = false)
    private AppUser follower;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "followee_id", nullable = false)
    private AppUser followee;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Follow() {
    }

    public Follow(AppUser follower, AppUser followee) {
        this.follower = follower;
        this.followee = followee;
    }

    public Long getId() {
        return id;
    }

    public AppUser getFollower() {
        return follower;
    }

    public AppUser getFollowee() {
        return followee;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
