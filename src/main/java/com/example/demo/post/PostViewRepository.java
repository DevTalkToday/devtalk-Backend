package com.example.demo.post;

import com.example.demo.auth.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostViewRepository extends JpaRepository<PostView, Long> {
    Optional<PostView> findByUserAndPost(AppUser user, Post post);

    void deleteByUser(AppUser user);

    void deleteByPost(Post post);
}
