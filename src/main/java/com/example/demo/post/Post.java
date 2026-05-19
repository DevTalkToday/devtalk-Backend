package com.example.demo.post;

import com.example.demo.auth.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "posts")
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 20)
    private String category;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(nullable = false)
    private int likeCount;

    @Column(nullable = false)
    private int bookmarkCount;

    @Column(nullable = false)
    private int viewCount;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> tags = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> majors = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("createdAt ASC")
    private List<PostComment> comments = new ArrayList<>();

    @Column(nullable = false)
    private boolean questionSolved;

    @Column(length = 180)
    private String questionEnvironment;

    @Column(length = 180)
    private String questionTried;

    @Column(length = 40)
    private String bugStatus;

    @Column(length = 10)
    private String bugPriority;

    @Column(length = 80)
    private String bugAssignee;

    @Column(length = 180)
    private String bugEnvironment;

    @Column(length = 240)
    private String bugExpected;

    @Column(length = 240)
    private String bugActual;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> bugReproductionSteps = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> bugLabels = new ArrayList<>();

    @Column(nullable = false)
    private int bugWatchers;

    @Column
    private Long acceptedCommentId;

    protected Post() {
    }

    public Post(String title, String content, String category, AppUser author) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.author = author;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getCategory() {
        return category;
    }

    public AppUser getAuthor() {
        return author;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getBookmarkCount() {
        return bookmarkCount;
    }

    public int getViewCount() {
        return viewCount;
    }

    public List<String> getTags() {
        return tags;
    }

    public List<String> getMajors() {
        return majors;
    }

    public List<PostComment> getComments() {
        return comments;
    }

    public boolean isQuestionSolved() {
        return questionSolved;
    }

    public String getQuestionEnvironment() {
        return questionEnvironment;
    }

    public String getQuestionTried() {
        return questionTried;
    }

    public String getBugStatus() {
        return bugStatus;
    }

    public String getBugPriority() {
        return bugPriority;
    }

    public String getBugAssignee() {
        return bugAssignee;
    }

    public String getBugEnvironment() {
        return bugEnvironment;
    }

    public String getBugExpected() {
        return bugExpected;
    }

    public String getBugActual() {
        return bugActual;
    }

    public List<String> getBugReproductionSteps() {
        return bugReproductionSteps;
    }

    public List<String> getBugLabels() {
        return bugLabels;
    }

    public int getBugWatchers() {
        return bugWatchers;
    }

    public Long getAcceptedCommentId() {
        return acceptedCommentId;
    }

    public void incrementViewCount() {
        this.viewCount += 1;
    }

    public void apply(PostPayload request) {
        this.title = request.title();
        this.content = request.content();
        this.category = request.category();
        this.tags = new ArrayList<>(request.tags());
        this.majors = new ArrayList<>(request.majors());
        this.updatedAt = Instant.now();

        this.questionSolved = false;
        this.questionEnvironment = null;
        this.questionTried = null;
        this.bugStatus = null;
        this.bugPriority = null;
        this.bugAssignee = null;
        this.bugEnvironment = null;
        this.bugExpected = null;
        this.bugActual = null;
        this.bugReproductionSteps = new ArrayList<>();
        this.bugLabels = new ArrayList<>();
        this.bugWatchers = 0;
        this.acceptedCommentId = null;

        if ("qna".equals(category) && request.question() != null) {
            this.questionSolved = request.question().solved();
            this.questionEnvironment = request.question().environment();
            this.questionTried = request.question().tried();
            this.acceptedCommentId = parseId(request.question().acceptedCommentId());
        }

        if ("bug".equals(category) && request.bug() != null) {
            this.bugStatus = request.bug().status();
            this.bugPriority = request.bug().priority();
            this.bugAssignee = request.bug().assignee();
            this.bugEnvironment = request.bug().environment();
            this.bugExpected = request.bug().expected();
            this.bugActual = request.bug().actual();
            this.bugReproductionSteps = new ArrayList<>(request.bug().reproductionSteps());
            this.bugLabels = new ArrayList<>(request.bug().labels());
            this.bugWatchers = request.bug().watchers();
            this.acceptedCommentId = parseId(request.bug().acceptedCommentId());
        }
    }

    public void addComment(PostComment comment) {
        comments.add(comment);
    }

    public void removeComment(PostComment comment) {
        comments.remove(comment);
    }

    public void setAcceptedCommentId(Long acceptedCommentId) {
        this.acceptedCommentId = acceptedCommentId;
        this.questionSolved = acceptedCommentId != null;
        this.updatedAt = Instant.now();
    }

    private static Long parseId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
