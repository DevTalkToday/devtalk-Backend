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

    long countByRecipientAndReadAtIsNull(AppUser recipient);

    @Query("""
            select m from Message m
            join fetch m.sender
            join fetch m.recipient
            where ((m.sender = :currentUser and m.recipient in :peers)
                or (m.sender in :peers and m.recipient = :currentUser))
              and m.id in (
                    select max(candidate.id) from Message candidate
                    where ((candidate.sender = :currentUser and candidate.recipient in :peers)
                        or (candidate.sender in :peers and candidate.recipient = :currentUser))
                    group by case
                        when candidate.sender = :currentUser then candidate.recipient.id
                        else candidate.sender.id
                    end
              )
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findLatestMessagesForPeers(
            @Param("currentUser") AppUser currentUser,
            @Param("peers") List<AppUser> peers
    );

    @Query("""
            select m.sender.id, count(m)
            from Message m
            where m.sender in :senders
              and m.recipient = :recipient
              and m.readAt is null
            group by m.sender.id
            """)
    List<Object[]> countUnreadBySenders(
            @Param("senders") List<AppUser> senders,
            @Param("recipient") AppUser recipient
    );

    void deleteBySenderOrRecipient(AppUser sender, AppUser recipient);
}
