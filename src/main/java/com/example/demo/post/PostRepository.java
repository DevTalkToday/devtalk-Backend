package com.example.demo.post;

import com.example.demo.auth.AppUser;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByAuthor(AppUser author);

    List<Post> findByAuthorOrderByCreatedAtDesc(AppUser author, Pageable pageable);

    long countByAuthor(AppUser author);

    List<Post> findByAuthorAndCategoryNotOrderByCreatedAtDesc(AppUser author, String category, Pageable pageable);

    long countByAuthorAndCategoryNot(AppUser author, String category);

    @Query("""
            select p.author.id, count(p)
            from Post p
            where p.author.id in :authorIds
            group by p.author.id
            """)
    List<Object[]> countByAuthorIds(@Param("authorIds") List<Long> authorIds);

    @Query(
            value = """
                    select p from Post p
                    where (:categoriesEmpty = true or p.category in :categories)
                      and (p.category <> 'talk' or (:viewerId is not null and p.author.id = :viewerId))
                      and (
                            :resolutionMode = 'ALL'
                         or (:resolutionMode = 'RESOLVED' and (
                                p.category = 'qna'
                             or (p.category = 'bug' and p.bugStatus in ('fixed', 'closed'))
                         ))
                         or (:resolutionMode = 'UNRESOLVED' and (
                                p.category = 'bug' and (p.bugStatus is null or p.bugStatus not in ('fixed', 'closed'))
                         ))
                         or (:resolutionMode = 'ANY_STATUS' and p.category in ('qna', 'bug'))
                      )
                    """,
            countQuery = """
                    select count(p) from Post p
                    where (:categoriesEmpty = true or p.category in :categories)
                      and (p.category <> 'talk' or (:viewerId is not null and p.author.id = :viewerId))
                      and (
                            :resolutionMode = 'ALL'
                         or (:resolutionMode = 'RESOLVED' and (
                                p.category = 'qna'
                             or (p.category = 'bug' and p.bugStatus in ('fixed', 'closed'))
                         ))
                         or (:resolutionMode = 'UNRESOLVED' and (
                                p.category = 'bug' and (p.bugStatus is null or p.bugStatus not in ('fixed', 'closed'))
                         ))
                         or (:resolutionMode = 'ANY_STATUS' and p.category in ('qna', 'bug'))
                      )
                    """
    )
    Page<Post> findListingPage(
            @Param("categories") List<String> categories,
            @Param("categoriesEmpty") boolean categoriesEmpty,
            @Param("resolutionMode") String resolutionMode,
            @Param("viewerId") Long viewerId,
            Pageable pageable
    );

    @Query("""
            select p from Post p
            where (:categoriesEmpty = true or p.category in :categories)
              and (p.category <> 'talk' or (:viewerId is not null and p.author.id = :viewerId))
              and (
                    :resolutionMode = 'ALL'
                 or (:resolutionMode = 'RESOLVED' and (
                        p.category = 'qna'
                     or (p.category = 'bug' and p.bugStatus in ('fixed', 'closed'))
                 ))
                 or (:resolutionMode = 'UNRESOLVED' and (
                        p.category = 'bug' and (p.bugStatus is null or p.bugStatus not in ('fixed', 'closed'))
                 ))
                 or (:resolutionMode = 'ANY_STATUS' and p.category in ('qna', 'bug'))
              )
            """)
    List<Post> findAllForListing(
            @Param("categories") List<String> categories,
            @Param("categoriesEmpty") boolean categoriesEmpty,
            @Param("resolutionMode") String resolutionMode,
            @Param("viewerId") Long viewerId
    );
}
