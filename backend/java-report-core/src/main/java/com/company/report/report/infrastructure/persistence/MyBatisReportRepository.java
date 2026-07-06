package com.company.report.report.infrastructure.persistence;

import com.company.report.report.domain.model.Report;
import com.company.report.report.domain.model.ReportStatus;
import com.company.report.report.domain.repository.ReportRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisReportRepository implements ReportRepository {
    private final ReportMapper mapper;

    public MyBatisReportRepository(ReportMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Report> findById(Long id) {
        ReportPo po = mapper.findById(id);
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(new Report(po.id(), po.title(), po.ownerUserId(),
                ReportStatus.valueOf(po.status().toUpperCase()), po.currentVersionId()));
    }

    @Override
    public Report save(Report report) {
        mapper.update(new ReportPo(report.id(), report.title(), report.ownerUserId(),
                report.status().name().toLowerCase(), report.currentVersionId()));
        return report;
    }

    @Override
    public List<Report> findByOwner(Long ownerUserId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        long offset = (long) (safePage - 1) * safePageSize;
        return mapper.findByOwner(ownerUserId, safePageSize, offset).stream()
                .map(po -> new Report(po.id(), po.title(), po.ownerUserId(),
                        ReportStatus.valueOf(po.status().toUpperCase()), po.currentVersionId()))
                .toList();
    }

    @Override
    public long countByOwner(Long ownerUserId) {
        return mapper.countByOwner(ownerUserId);
    }
}
