package com.example.demo.profile;

import static com.example.demo.ControllerTestSupport.mockMvc;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class ProfileControllerTest {
    private final ProfileService profileService = mock(ProfileService.class);
    private final AuthService authService = mock(AuthService.class);
    private final MockMvc mvc = mockMvc(new ProfileController(profileService, authService));

    @Test
    void meRequiresBearerToken() throws Exception {
        mvc.perform(get("/profile/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bearer token is required"));
    }

    @Test
    void bookmarksRequireBearerToken() throws Exception {
        mvc.perform(get("/profile/me/bookmarks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bearer token is required"));
    }

    @Test
    void updateProfileValidatesNickname() throws Exception {
        mvc.perform(patch("/profile/me")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"","description":"hello","majors":["backend"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void publicPostsDoesNotRequireBearerToken() throws Exception {
        mvc.perform(get("/profile/10/posts").param("page", "1").param("limit", "12"))
                .andExpect(status().isOk());
    }
}
