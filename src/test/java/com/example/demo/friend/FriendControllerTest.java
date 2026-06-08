package com.example.demo.friend;

import static com.example.demo.ControllerTestSupport.mockMvc;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class FriendControllerTest {
    private final FriendService friendService = mock(FriendService.class);
    private final AuthService authService = mock(AuthService.class);
    private final MockMvc mvc = mockMvc(new FriendController(friendService, authService));

    @Test
    void summaryRequiresBearerToken() throws Exception {
        mvc.perform(get("/friends"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bearer token is required"));
    }

    @Test
    void receivedCountRequiresBearerToken() throws Exception {
        mvc.perform(get("/friends/received-count"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bearer token is required"));
    }

    @Test
    void requestValidatesTargetUserId() throws Exception {
        mvc.perform(post("/friends/requests")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
