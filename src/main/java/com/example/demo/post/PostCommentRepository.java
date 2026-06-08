package com.example.demo.post;

import com.example.demo.auth.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {
    Optional<PostComment> findByIdAndPostId(Long id, Long postId);

    List<PostComment> findByAuthor(AppUser author);

    List<PostComment> findByAuthorOrderByCreatedAtDesc(AppUser author, Pageable pageable);

    long countByAuthor(AppUser author);

    List<PostComment> findByAuthorAndPostCategoryNotOrderByCreatedAtDesc(AppUser author, String category, Pageable pageable);

    long countByAuthorAndPostCategoryNot(AppUser author, String category);

    @Query("""
            select c.author.id, count(c)
            from PostComment c
            where c.author.id in :authorIds
            group by c.author.id
            """)
    List<Object[]> countByAuthorIds(@Param("authorIds") List<Long> authorIds);

    @Query("select count(c) from PostComment c where c.author = :author and c.post.acceptedCommentId = c.id")
    long countAcceptedByAuthor(@Param("author") AppUser author);
}
