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
                "Next.js 배포 후 stale 캐시를 정리한 해결 기록",
                "배포 직후 이전 페이지가 남아 있던 문제를 정리한 기록입니다. 캐시 무효화 시점과 stale 응답 조건을 추적해 해결했습니다.",
                "qna",
                List.of("nextjs", "cache", "deploy"),
                List.of("프론트엔드"),
                new PostPayload.QuestionPayload(
                        "배포 직후 최신 페이지 데이터가 바로 노출되어야 합니다.",
                        "첫 요청에서 stale 응답이 남아 구버전 화면이 잠깐 보였습니다.",
                        List.of("메인 페이지 접속", "구버전 데이터 노출 확인", "캐시 재검증 후 최신 데이터 확인"),
                        null
                ),
                null
        );

        PostPayload bug = new PostPayload(
                "Markdown 이미지 미리보기에서 메모리가 계속 증가하는 도움 필요",
                "이미지 첨부와 제거를 반복하면 브라우저 메모리가 계속 증가합니다. Blob URL 정리 누락 가능성을 의심하고 있습니다.",
                "bug",
                List.of("editor", "memory", "blob"),
                List.of("프론트엔드", "QA"),
                null,
                new PostPayload.BugPayload(
                        "investigating",
                        "이미지 제거 시 메모리 사용량이 안정적으로 회수되어야 합니다.",
                        "이미지 제거 후에도 메모리 사용량이 계속 증가합니다.",
                        List.of("이미지 첨부", "이미지 제거", "메모리 사용량 확인"),
                        2,
                        null
                )
        );

        PostPayload talk = new PostPayload(
                "같은 인증 오류를 반복하지 않도록 대응 내용을 남깁니다",
                "토큰 만료 처리와 게스트 세션 흐름이 뒤섞여 같은 인증 문제가 반복됐습니다. 요청 레이어와 보호 로직을 분리한 회고입니다.",
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
