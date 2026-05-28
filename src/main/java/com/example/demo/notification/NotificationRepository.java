package com.example.demo.notification;

import com.example.demo.auth.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Query("""
            select n from Notification n
            left join fetch n.actor
            where n.recipient = :recipient
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findByRecipient(@Param("recipient") AppUser recipient, Pageable pageable);

    @Query("""
            select n from Notification n
            left join fetch n.actor
            where n.id = :id
              and n.recipient = :recipient
            """)
    Optional<Notification> findByIdAndRecipient(@Param("id") Long id, @Param("recipient") AppUser recipient);

    List<Notification> findByRecipientAndReadAtIsNull(AppUser recipient);

    long countByRecipientAndReadAtIsNull(AppUser recipient);

    void deleteByRecipientOrActor(AppUser recipient, AppUser actor);
}
