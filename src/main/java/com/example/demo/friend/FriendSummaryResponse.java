package com.example.demo.friend;

import java.util.List;

public record FriendSummaryResponse(
        List<FriendshipResponse> friends,
        List<FriendshipResponse> received,
        List<FriendshipResponse> sent
) {
}
