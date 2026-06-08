package com.example.demo.post;

import com.example.demo.auth.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostCommentLikeRepository extends JpaRepository<PostCommentLike, Long> {
    Optional<PostCommentLike> findByCommentAndUser(PostComment comment, AppUser user);

    void deleteByComment(PostComment comment);

    void deleteByCommentPost(Post post);

    void deleteByUser(AppUser user);

    @Query("""
            select l.comment.id
            from PostCommentLike l
            where l.user = :user
              and l.comment.id in :commentIds
            """)
    List<Long> findLikedCommentIds(@Param("user") AppUser user, @Param("commentIds") List<Long> commentIds);
}
