package com.company.report.audit.domain.repository;

import com.company.report.audit.domain.model.ModelInvocationAudit;

import java.util.Optional;

public interface ModelInvocationRepository {
    ModelInvocationAudit save(ModelInvocationAudit invocation);

    Optional<ModelInvocationAudit> findById(Long id);
}
