package com.example.demo.message;

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

class MessageControllerTest {
    private final MessageService messageService = mock(MessageService.class);
    private final AuthService authService = mock(AuthService.class);
    private final MockMvc mvc = mockMvc(new MessageController(messageService, authService));

    @Test
    void conversationsRequireBearerToken() throws Exception {
        mvc.perform(get("/messages/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bearer token is required"));
    }

    @Test
    void unreadCountRequiresBearerToken() throws Exception {
        mvc.perform(get("/messages/unread-count"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bearer token is required"));
    }

    @Test
    void sendMessageValidatesRecipientAndBody() throws Exception {
        mvc.perform(post("/messages")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
