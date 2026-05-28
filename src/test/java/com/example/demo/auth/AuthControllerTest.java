package com.example.demo.auth;

import static com.example.demo.ControllerTestSupport.mockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.auth.dto.AuthResponse;
import com.example.demo.auth.dto.SignupRequest;
import com.example.demo.auth.dto.UserResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class AuthControllerTest {
    private final AuthService authService = mock(AuthService.class);
    private final MockMvc mvc = mockMvc(new AuthController(authService));

    @Test
    void signupReturnsCreatedAuthResponse() throws Exception {
        UserResponse user = new UserResponse(1L, "user@example.com", "User", "user@example.com", null, null, true, List.of("backend"), Instant.EPOCH, false);
        when(authService.signup(any(SignupRequest.class))).thenReturn(new AuthResponse("token", "Bearer", 7200, user));

        mvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"user@example.com","password":"password123","nickname":"User","majors":["backend"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("token"))
                .andExpect(jsonPath("$.user.username").value("user@example.com"));

        verify(authService).signup(any(SignupRequest.class));
    }

    @Test
    void meRequiresBearerToken() throws Exception {
        mvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bearer token is required"));
    }

    @Test
    void signupValidatesEmailAndPassword() throws Exception {
        mvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"not-email","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
