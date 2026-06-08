package com.example.demo.post;

import com.example.demo.auth.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostBookmarkRepository extends JpaRepository<PostBookmark, Long> {
    Optional<PostBookmark> findByPostAndUser(Post post, AppUser user);

    boolean existsByPostAndUser(Post post, AppUser user);

    void deleteByUser(AppUser user);

    void deleteByPost(Post post);

    @Query("""
            select b.post.id
            from PostBookmark b
            where b.user = :user
              and b.post.id in :postIds
            """)
    List<Long> findBookmarkedPostIds(@Param("user") AppUser user, @Param("postIds") List<Long> postIds);

    @Query(
            value = """
                    select b from PostBookmark b
                    join fetch b.post p
                    where b.user = :user
                      and (p.category <> 'talk' or p.author = :user)
                    """,
            countQuery = """
                    select count(b) from PostBookmark b
                    join b.post p
                    where b.user = :user
                      and (p.category <> 'talk' or p.author = :user)
                    """
    )
    Page<PostBookmark> findReadableByUser(@Param("user") AppUser user, Pageable pageable);
}
