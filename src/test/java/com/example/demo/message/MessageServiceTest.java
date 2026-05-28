package com.example.demo.message;

import static com.example.demo.TestSupport.withId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.auth.AppUser;
import com.example.demo.auth.UserRepository;
import com.example.demo.friend.Friendship;
import com.example.demo.friend.FriendshipRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class MessageServiceTest {
    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FriendshipRepository friendshipRepository = mock(FriendshipRepository.class);
    private final MessageService service = new MessageService(messageRepository, userRepository, friendshipRepository);

    @Test
    void sendMessageRejectsSelfRecipient() {
        AppUser currentUser = user(1L, "me@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.sendMessage(currentUser, new MessagePayload(1L, "hello"))
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals("Cannot send a message to yourself", error.getReason());
    }

    @Test
    void sendMessageRejectsNonAcceptedFriendship() {
        AppUser currentUser = user(1L, "me@example.com");
        AppUser recipient = user(2L, "other@example.com");
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
        when(friendshipRepository.findBetween(currentUser, recipient))
                .thenReturn(Optional.of(new Friendship(currentUser, recipient)));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.sendMessage(currentUser, new MessagePayload(2L, "hello"))
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals("Only accepted friends can message each other", error.getReason());
    }

    @Test
    void sendMessageTrimsBodyWhenFriendshipIsAccepted() {
        AppUser currentUser = user(1L, "me@example.com");
        AppUser recipient = user(2L, "other@example.com");
        Friendship friendship = new Friendship(currentUser, recipient);
        friendship.accept();
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
        when(friendshipRepository.findBetween(currentUser, recipient)).thenReturn(Optional.of(friendship));
        when(messageRepository.save(org.mockito.ArgumentMatchers.any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            return withId(message, 30L);
        });

        MessageResponse response = service.sendMessage(currentUser, new MessagePayload(2L, "  hello  "));

        assertEquals(30L, response.id());
        assertEquals("hello", response.body());
        assertTrue(response.mine());
    }

    private static AppUser user(Long id, String email) {
        return withId(new AppUser(email, email, email, "hash", true, List.of()), id);
    }
}
