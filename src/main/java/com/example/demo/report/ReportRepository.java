package com.example.demo.report;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByOrderByCreatedAtDescIdDesc(Pageable pageable);

    void deleteByReporterId(Long reporterId);

    void deleteByTargetTypeAndTargetId(String targetType, String targetId);
}
