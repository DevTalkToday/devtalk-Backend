package com.example.demo.post;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {
    Optional<PostComment> findByIdAndPostId(Long id, Long postId);
}
