package com.example.demo.friend;

import com.example.demo.auth.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    @Query("""
            select f from Friendship f
            join fetch f.requester
            join fetch f.addressee
            where (f.requester = :user or f.addressee = :user)
              and f.status = :status
            order by coalesce(f.respondedAt, f.createdAt) desc
            """)
    List<Friendship> findByUserAndStatus(@Param("user") AppUser user, @Param("status") FriendshipStatus status);

    @Query("""
            select f from Friendship f
            join fetch f.requester
            join fetch f.addressee
            where f.addressee = :user
              and f.status = 'PENDING'
            order by f.createdAt desc
            """)
    List<Friendship> findReceivedRequests(@Param("user") AppUser user);

    @Query("""
            select f from Friendship f
            join fetch f.requester
            join fetch f.addressee
            where f.requester = :user
              and f.status = 'PENDING'
            order by f.createdAt desc
            """)
    List<Friendship> findSentRequests(@Param("user") AppUser user);

    @Query("""
            select f from Friendship f
            where (f.requester = :first and f.addressee = :second)
               or (f.requester = :second and f.addressee = :first)
            """)
    Optional<Friendship> findBetween(@Param("first") AppUser first, @Param("second") AppUser second);

    void deleteByRequesterOrAddressee(AppUser requester, AppUser addressee);
}
