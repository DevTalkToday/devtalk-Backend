package com.example.demo.post;

import com.example.demo.auth.AppUser;
import com.example.demo.auth.UserRepository;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class PostSeedData implements CommandLineRunner {
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public PostSeedData(PostRepository postRepository, UserRepository userRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        if (postRepository.count() > 0) return;

        AppUser author = userRepository.findByUsernameIgnoreCase("seed_writer")
                .orElseGet(() -> userRepository.save(new AppUser(
                        "seed_writer",
                        "Devtalk",
                        "seed@example.com",
                        null,
                        true,
                        List.of("프론트엔드", "백엔드")
                )));

        PostPayload qna = new PostPayload(
                "Next.js 배포 후 라우트 캐시가 갱신되지 않은 기록",
                "배포 직후 일부 사용자가 이전 페이지 데이터를 계속 보는 문제가 있었습니다. 라우트 캐시와 요청 캐시 무효화 타이밍을 분리해서 확인했고, 배포 후 첫 요청에서 stale 데이터가 남는 조건을 찾았습니다.",
                "qna",
                List.of("nextjs", "cache", "deploy"),
                List.of("프론트엔드"),
                new PostPayload.QuestionPayload(false, "Next.js 16 / Vercel", "router.refresh와 revalidatePath를 확인", null),
                null
        );

        PostPayload bug = new PostPayload(
                "Markdown 이미지 미리보기에서 메모리가 계속 증가한 에러",
                "이미지 파일을 반복해서 첨부하고 제거하면 브라우저 메모리가 계속 증가했습니다. Blob URL 해제가 누락되어 있었고, 컴포넌트 unmount와 파일 교체 시점에 revokeObjectURL을 호출하도록 수정했습니다.",
                "bug",
                List.of("editor", "memory", "blob"),
                List.of("프론트엔드", "QA"),
                null,
                new PostPayload.BugPayload("investigating", "P1", "@frontend", "Chrome / Windows", "이미지 제거 시 메모리 반환", "메모리 사용량 지속 증가", List.of("이미지 첨부", "이미지 제거", "메모리 확인"), List.of("editor", "performance"), 2, null)
        );

        PostPayload talk = new PostPayload(
                "같은 인증 에러가 반복되지 않도록 남긴 팀 회고",
                "토큰 만료 처리와 게스트 세션 처리가 섞이면서 같은 인증 문제가 반복되었습니다. 요청 레이어에서 토큰 발급과 로그인 사용자 판별을 분리하고, 보호 기능은 명시적으로 로그인 상태를 요구하도록 정리했습니다.",
                "talk",
                List.of("auth", "retrospective"),
                List.of("백엔드"),
                null,
                null
        );

        saveSeed(author, qna, 12, 3, 42);
        saveSeed(author, bug, 18, 5, 67);
        saveSeed(author, talk, 9, 2, 31);
    }

    private void saveSeed(AppUser author, PostPayload payload, int likes, int bookmarks, int views) {
        Post post = new Post(payload.title(), payload.content(), payload.category(), author);
        post.apply(payload);
        for (int i = 0; i < views; i += 1) post.incrementViewCount();
        postRepository.save(post);
    }
}
