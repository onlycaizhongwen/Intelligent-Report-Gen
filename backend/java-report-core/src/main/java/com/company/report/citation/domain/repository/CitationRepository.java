package com.company.report.citation.domain.repository;

import com.company.report.citation.domain.model.CitationSource;

import java.util.Optional;

public interface CitationRepository {
    Optional<CitationSource> findSourceById(Long id);
}
