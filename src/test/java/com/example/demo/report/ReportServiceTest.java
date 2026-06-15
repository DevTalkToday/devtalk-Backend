package com.example.demo.report;

import static com.example.demo.TestSupport.withId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReportServiceTest {
    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final ReportService service = new ReportService(reportRepository);

    @Test
    void submitStripsUnsafeTargetUrl() {
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));

        service.submit(
                new ReportPayload("post", "10", "Suspicious post", "javascript:alert(1)", "spam", "details"),
                null
        );

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertEquals("", captor.getValue().getTargetUrl());
    }

    @Test
    void submitKeepsSafeRelativeTargetUrl() {
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));

        service.submit(
                new ReportPayload("comment", "11", "Comment", "/posts/11?focus=comment-2", "abuse", "details"),
                null
        );

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertEquals("/posts/11?focus=comment-2", captor.getValue().getTargetUrl());
    }
}
