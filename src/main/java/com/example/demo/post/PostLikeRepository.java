package com.example.demo.post;

import com.example.demo.auth.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    Optional<PostLike> findByPostAndUser(Post post, AppUser user);

    boolean existsByPostAndUser(Post post, AppUser user);

    void deleteByUser(AppUser user);

    void deleteByPost(Post post);

    @Query("""
            select l.post.id
            from PostLike l
            where l.user = :user
              and l.post.id in :postIds
            """)
    List<Long> findLikedPostIds(@Param("user") AppUser user, @Param("postIds") List<Long> postIds);
}
