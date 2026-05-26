package com.example.demo.post;

import com.example.demo.auth.AppUser;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByAuthorOrderByCreatedAtDesc(AppUser author, Pageable pageable);

    long countByAuthor(AppUser author);
}
