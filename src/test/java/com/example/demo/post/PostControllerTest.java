package com.example.demo.post;

import static com.example.demo.ControllerTestSupport.mockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.auth.AppUser;
import com.example.demo.auth.AuthService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class PostControllerTest {
    private final PostService postService = mock(PostService.class);
    private final AuthService authService = mock(AuthService.class);
    private final MockMvc mvc = mockMvc(new PostController(postService, authService));

    @Test
    void listPostsPassesQueryParametersToService() throws Exception {
        when(postService.listPosts("bug", "login", "popular", "unresolved", "or", 2, 12, null))
                .thenReturn(new PostListResponse(List.of(), new PostListResponse.PageInfo(2, 12, 0, 1, false, true)));

        mvc.perform(get("/posts")
                        .param("category", "bug")
                        .param("q", "login")
                        .param("sort", "popular")
                        .param("resolution", "unresolved")
                        .param("match", "or")
                        .param("page", "2")
                        .param("limit", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.page").value(2))
                .andExpect(jsonPath("$.pageInfo.limit").value(12));
    }

    @Test
    void createPostRequiresBearerToken() throws Exception {
        mvc.perform(post("/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Title","content":"Body","category":"talk","tags":[],"majors":[]}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bearer token is required"));

        verify(postService, never()).createPost(any(PostPayload.class), any(AppUser.class));
    }

    @Test
    void bookmarkRequiresBearerToken() throws Exception {
        mvc.perform(post("/posts/10/bookmark"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bearer token is required"));
    }

    @Test
    void getPostAllowsAnonymousViewer() throws Exception {
        PostResponse response = new PostResponse(
                "10", "Title", "Body", "Body", "bug", null, null, null,
                0, 0, 0, false, 0, List.of(), List.of(), List.of(), null, null, false, false, false
        );
        when(postService.getPost(10L, false, null)).thenReturn(response);

        mvc.perform(get("/posts/10").param("track", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("10"));

        verify(postService).getPost(eq(10L), eq(false), eq(null));
    }
}
