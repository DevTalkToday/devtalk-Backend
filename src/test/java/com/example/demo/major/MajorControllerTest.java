package com.example.demo.major;

import static com.example.demo.ControllerTestSupport.mockMvc;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class MajorControllerTest {
    private final MajorService majorService = mock(MajorService.class);
    private final MockMvc mvc = mockMvc(new MajorController(majorService));

    @Test
    void majorsArePublic() throws Exception {
        when(majorService.listMajors()).thenReturn(new MajorListResponse(List.of(new MajorResponse("backend", "백엔드"))));

        mvc.perform(get("/majors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("backend"))
                .andExpect(jsonPath("$.items[0].label").value("백엔드"));
    }
}
