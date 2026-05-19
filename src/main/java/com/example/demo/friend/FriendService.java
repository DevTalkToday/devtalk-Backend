package com.example.demo.friend;

import com.example.demo.auth.AppUser;
import com.example.demo.auth.UserRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FriendService {
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    public FriendService(FriendshipRepository friendshipRepository, UserRepository userRepository) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public FriendSummaryResponse getSummary(AppUser currentUser) {
        List<FriendshipResponse> friends = friendshipRepository
                .findByUserAndStatus(currentUser, FriendshipStatus.ACCEPTED)
                .stream()
                .map(friendship -> FriendshipResponse.from(friendship, currentUser))
                .toList();

        List<FriendshipResponse> received = friendshipRepository
                .findReceivedRequests(currentUser)
                .stream()
                .map(friendship -> FriendshipResponse.from(friendship, currentUser))
                .toList();

        List<FriendshipResponse> sent = friendshipRepository
                .findSentRequests(currentUser)
                .stream()
                .map(friendship -> FriendshipResponse.from(friendship, currentUser))
                .toList();

        return new FriendSummaryResponse(friends, received, sent);
    }

    @Transactional
    public List<FriendSearchResponse> search(AppUser currentUser, String keyword) {
        String q = keyword == null ? "" : keyword.trim();
        if (q.length() < 2) {
            return List.of();
        }

        return userRepository.searchUsers(q, currentUser.getId())
                .stream()
                .limit(20)
                .map(user -> {
                    Friendship friendship = friendshipRepository.findBetween(currentUser, user).orElse(null);
                    return new FriendSearchResponse(
                            FriendUserResponse.from(user),
                            relationshipOf(friendship, currentUser),
                            friendship == null ? null : friendship.getId()
                    );
                })
                .toList();
    }

    @Transactional
    public FriendshipResponse request(AppUser currentUser, FriendRequestPayload payload) {
        AppUser target = userRepository.findById(payload.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (currentUser.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot add yourself as a friend");
        }

        Friendship existing = friendshipRepository.findBetween(currentUser, target).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == FriendshipStatus.ACCEPTED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Already friends");
            }
            if (existing.getAddressee().getId().equals(currentUser.getId())) {
                existing.accept();
                return FriendshipResponse.from(existing, currentUser);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Friend request already sent");
        }

        Friendship friendship = friendshipRepository.save(new Friendship(currentUser, target));
        return FriendshipResponse.from(friendship, currentUser);
    }

    @Transactional
    public FriendshipResponse accept(AppUser currentUser, Long requestId) {
        Friendship friendship = getFriendship(requestId);
        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Friend request is not pending");
        }
        if (!friendship.getAddressee().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the recipient can accept this request");
        }

        friendship.accept();
        return FriendshipResponse.from(friendship, currentUser);
    }

    @Transactional
    public void delete(AppUser currentUser, Long friendshipId) {
        Friendship friendship = getFriendship(friendshipId);
        if (!friendship.getRequester().getId().equals(currentUser.getId())
                && !friendship.getAddressee().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot modify this friendship");
        }

        friendshipRepository.delete(friendship);
    }

    private Friendship getFriendship(Long id) {
        return friendshipRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Friendship not found"));
    }

    private static String relationshipOf(Friendship friendship, AppUser currentUser) {
        if (friendship == null) return "NONE";
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) return "FRIEND";
        if (friendship.getRequester().getId().equals(currentUser.getId())) return "SENT";
        return "RECEIVED";
    }
}
