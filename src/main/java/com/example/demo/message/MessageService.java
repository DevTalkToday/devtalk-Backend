package com.example.demo.message;

import com.example.demo.auth.AppUser;
import com.example.demo.auth.UserRepository;
import com.example.demo.friend.Friendship;
import com.example.demo.friend.FriendshipRepository;
import com.example.demo.friend.FriendshipStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class MessageService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_BODY_LENGTH = 2000;

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;

    public MessageService(
            MessageRepository messageRepository,
            UserRepository userRepository,
            FriendshipRepository friendshipRepository
    ) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(AppUser currentUser) {
        List<AppUser> peers = friendshipRepository.findByUserAndStatus(currentUser, FriendshipStatus.ACCEPTED)
                .stream()
                .map(friendship -> peerOf(friendship, currentUser))
                .toList();
        if (peers.isEmpty()) {
            return List.of();
        }

        Map<Long, Message> latestMessages = messageRepository.findLatestMessagesForPeers(currentUser, peers)
                .stream()
                .collect(Collectors.toMap(
                        message -> peerIdOf(message, currentUser),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<Long, Integer> unreadCounts = messageRepository.countUnreadBySenders(peers, currentUser)
                .stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).intValue()
                ));

        return peers.stream()
                .map(peer -> toConversationResponse(currentUser, peer, latestMessages.get(peer.getId()), unreadCounts.getOrDefault(peer.getId(), 0)))
                .sorted(Comparator
                        .comparing(MessageService::lastMessageCreatedAt, Comparator.reverseOrder())
                .thenComparing(response -> response.user().nickname(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public MessageUnreadCountResponse unreadCount(AppUser currentUser) {
        return new MessageUnreadCountResponse(messageRepository.countByRecipientAndReadAtIsNull(currentUser));
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getConversation(AppUser currentUser, Long userId, int limit) {
        AppUser peer = findUser(userId);
        ensureCanMessage(currentUser, peer);

        List<Message> newestFirst = messageRepository.findConversation(
                currentUser,
                peer,
                PageRequest.of(0, normalizeLimit(limit))
        );
        List<Message> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);

        return chronological.stream()
                .map(message -> toMessageResponse(message, currentUser))
                .toList();
    }

    @Transactional
    public MessageResponse sendMessage(AppUser currentUser, MessagePayload payload) {
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "MESSAGE_BODY_REQUIRED");
        }

        AppUser recipient = findUser(payload.recipientId());
        if (currentUser.getId().equals(recipient.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot send a message to yourself");
        }
        ensureCanMessage(currentUser, recipient);

        Message message = messageRepository.save(new Message(currentUser, recipient, normalizeBody(payload.body())));
        return toMessageResponse(message, currentUser);
    }

    @Transactional
    public MessageReadResponse markConversationRead(AppUser currentUser, Long userId) {
        AppUser peer = findUser(userId);
        ensureCanMessage(currentUser, peer);

        List<Message> unread = messageRepository.findUnreadFrom(peer, currentUser);
        unread.forEach(Message::markRead);
        return new MessageReadResponse(peer.getId(), unread.size());
    }

    private ConversationResponse toConversationResponse(AppUser currentUser, AppUser peer, Message latest, int unreadCount) {
        return new ConversationResponse(
                MessageUserResponse.from(peer),
                latest == null ? null : toMessageResponse(latest, currentUser),
                unreadCount
        );
    }

    private AppUser findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void ensureCanMessage(AppUser currentUser, AppUser peer) {
        Friendship friendship = friendshipRepository.findBetween(currentUser, peer)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Only friends can message each other"));

        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only accepted friends can message each other");
        }
    }

    private MessageResponse toMessageResponse(Message message, AppUser currentUser) {
        return new MessageResponse(
                message.getId(),
                MessageUserResponse.from(message.getSender()),
                MessageUserResponse.from(message.getRecipient()),
                message.getBody(),
                message.getCreatedAt(),
                message.getReadAt(),
                message.getSender().getId().equals(currentUser.getId())
        );
    }

    private static AppUser peerOf(Friendship friendship, AppUser currentUser) {
        return friendship.getRequester().getId().equals(currentUser.getId())
                ? friendship.getAddressee()
                : friendship.getRequester();
    }

    private static Long peerIdOf(Message message, AppUser currentUser) {
        return message.getSender().getId().equals(currentUser.getId())
                ? message.getRecipient().getId()
                : message.getSender().getId();
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }

    private static String normalizeBody(String body) {
        String normalized = body == null ? "" : body.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "MESSAGE_BODY_REQUIRED");
        }
        if (normalized.length() > MAX_BODY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "MESSAGE_BODY_TOO_LONG");
        }
        return normalized;
    }

    private static Instant lastMessageCreatedAt(ConversationResponse response) {
        return response.lastMessage() == null ? Instant.EPOCH : response.lastMessage().createdAt();
    }
}
