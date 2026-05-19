package com.example.demo.major;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MajorService {
    private static final List<MajorResponse> MAJORS = List.of(
            new MajorResponse("frontend", "프론트엔드"),
            new MajorResponse("backend", "백엔드"),
            new MajorResponse("ai", "AI"),
            new MajorResponse("devops", "DevOps"),
            new MajorResponse("design", "디자인"),
            new MajorResponse("mobile", "모바일"),
            new MajorResponse("qa", "QA")
    );

    public MajorListResponse listMajors() {
        return new MajorListResponse(MAJORS);
    }
}
