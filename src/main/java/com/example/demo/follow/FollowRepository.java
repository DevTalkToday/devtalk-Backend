package com.example.demo.follow;

import com.example.demo.auth.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    Optional<Follow> findByFollowerAndFollowee(AppUser follower, AppUser followee);

    boolean existsByFollowerAndFollowee(AppUser follower, AppUser followee);

    long countByFollower(AppUser follower);

    long countByFollowee(AppUser followee);

    List<Follow> findByFollowee(AppUser followee);

    void deleteByFollowerOrFollowee(AppUser follower, AppUser followee);
}
