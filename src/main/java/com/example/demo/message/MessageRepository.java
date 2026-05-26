package com.example.demo.message;

import com.example.demo.auth.AppUser;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {
    @Query("""
            select m from Message m
            join fetch m.sender
            join fetch m.recipient
            where (m.sender = :first and m.recipient = :second)
               or (m.sender = :second and m.recipient = :first)
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findConversation(
            @Param("first") AppUser first,
            @Param("second") AppUser second,
            Pageable pageable
    );

    @Query("""
            select m from Message m
            join fetch m.sender
            join fetch m.recipient
            where m.sender = :sender
              and m.recipient = :recipient
              and m.readAt is null
            order by m.createdAt asc, m.id asc
            """)
    List<Message> findUnreadFrom(@Param("sender") AppUser sender, @Param("recipient") AppUser recipient);

    long countBySenderAndRecipientAndReadAtIsNull(AppUser sender, AppUser recipient);
}
