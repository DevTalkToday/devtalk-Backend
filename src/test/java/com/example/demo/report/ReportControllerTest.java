package com.example.demo.report;

import static com.example.demo.ControllerTestSupport.mockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.auth.AuthService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class ReportControllerTest {
    private final ReportService reportService = mock(ReportService.class);
    private final AuthService authService = mock(AuthService.class);
    private final MockMvc mvc = mockMvc(new ReportController(reportService, authService));

    @Test
    void submitAllowsAnonymousReporter() throws Exception {
        when(reportService.submit(any(ReportPayload.class), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(new ReportSubmissionResponse(3L, "received", Instant.EPOCH));

        mvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"post","targetId":"10","subject":"spam","content":"bad content"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.status").value("received"));
    }

    @Test
    void listRequiresBearerToken() throws Exception {
        mvc.perform(get("/reports"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bearer token is required"));
    }
}
