package com.lostfound.backend.moderation;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByItemIdAndReporterId(Long itemId, Long reporterId);

    @EntityGraph(attributePaths = {"item", "item.owner", "reporter", "handledBy"})
    List<Report> findByStatusOrderByCreatedAtDesc(Report.Status status, Pageable pageable);

    List<Report> findByItemIdAndStatus(Long itemId, Report.Status status);

    long countByStatus(Report.Status status);
}
