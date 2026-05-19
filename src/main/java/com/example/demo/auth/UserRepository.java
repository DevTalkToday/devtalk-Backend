package com.example.demo.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsernameIgnoreCase(String username);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    @Query("""
            select u from AppUser u
            where u.id <> :currentUserId
              and u.profileCompleted = true
              and u.username <> '__guest__'
              and (
                    lower(u.nickname) like lower(concat('%', :keyword, '%'))
                 or lower(u.username) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(u.email, '')) like lower(concat('%', :keyword, '%'))
              )
            order by u.nickname asc
            """)
    List<AppUser> searchUsers(@Param("keyword") String keyword, @Param("currentUserId") Long currentUserId);
}
