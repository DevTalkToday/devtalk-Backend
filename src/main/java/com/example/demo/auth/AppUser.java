package com.example.demo.auth;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(nullable = false, length = 120)
    private String nickname;

    @Column(length = 255)
    private String email;

    @Column
    private String passwordHash;

    @Column(length = 500)
    private String description;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String avatarUrl;

    @Column(nullable = false)
    private boolean profileCompleted;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_majors", joinColumns = @JoinColumn(name = "app_user_id"))
    @Column(name = "majors")
    private List<String> majors = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected AppUser() {
    }

    public AppUser(String username, String nickname, String passwordHash, List<String> majors) {
        this.username = username;
        this.nickname = nickname;
        this.passwordHash = passwordHash;
        this.profileCompleted = true;
        this.majors = majors == null ? new ArrayList<>() : new ArrayList<>(majors);
    }

    public AppUser(String username, String nickname, String email, String passwordHash, boolean profileCompleted, List<String> majors) {
        this.username = username;
        this.nickname = nickname;
        this.email = email;
        this.passwordHash = passwordHash;
        this.profileCompleted = profileCompleted;
        this.majors = majors == null ? new ArrayList<>() : new ArrayList<>(majors);
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getNickname() {
        return nickname;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDescription() {
        return description;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public boolean isProfileCompleted() {
        return profileCompleted;
    }

    public List<String> getMajors() {
        return majors;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void completeProfile(String nickname, String passwordHash, String description, List<String> majors) {
        this.nickname = nickname;
        this.passwordHash = passwordHash;
        this.description = description;
        this.majors = majors == null ? new ArrayList<>() : new ArrayList<>(majors);
        this.profileCompleted = true;
    }

    public void updateProfile(String nickname, String description, List<String> majors) {
        this.nickname = nickname;
        this.description = description;
        this.majors = majors == null ? new ArrayList<>() : new ArrayList<>(majors);
    }

    public void updateAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
