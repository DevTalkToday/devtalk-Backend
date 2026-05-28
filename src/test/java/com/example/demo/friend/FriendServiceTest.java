package com.example.demo.friend;

import static com.example.demo.TestSupport.withId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.auth.AppUser;
import com.example.demo.auth.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class FriendServiceTest {
    private final FriendshipRepository friendshipRepository = mock(FriendshipRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FriendService service = new FriendService(friendshipRepository, userRepository);

    @Test
    void requestRejectsSelfFriendship() {
        AppUser currentUser = user(1L, "me@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.request(currentUser, new FriendRequestPayload(1L))
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals("Cannot add yourself as a friend", error.getReason());
        verify(friendshipRepository, never()).save(org.mockito.ArgumentMatchers.any(Friendship.class));
    }

    @Test
    void requestAcceptsIncomingPendingRequestInsteadOfDuplicating() {
        AppUser currentUser = user(1L, "me@example.com");
        AppUser requester = user(2L, "other@example.com");
        Friendship incoming = withId(new Friendship(requester, currentUser), 10L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(requester));
        when(friendshipRepository.findBetween(currentUser, requester)).thenReturn(Optional.of(incoming));

        FriendshipResponse response = service.request(currentUser, new FriendRequestPayload(2L));

        assertEquals(FriendshipStatus.ACCEPTED, incoming.getStatus());
        assertEquals(FriendshipStatus.ACCEPTED, response.status());
        verify(friendshipRepository, never()).save(org.mockito.ArgumentMatchers.any(Friendship.class));
    }

    private static AppUser user(Long id, String email) {
        return withId(new AppUser(email, email, email, "hash", true, List.of()), id);
    }
}
