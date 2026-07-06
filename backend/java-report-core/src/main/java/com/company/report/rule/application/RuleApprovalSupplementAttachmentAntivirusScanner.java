package com.company.report.rule.application;

import java.io.IOException;

public interface RuleApprovalSupplementAttachmentAntivirusScanner {
    RuleApprovalSupplementAttachmentInspector.InspectionResult scan(byte[] fileBytes, String contentType, String fileName) throws IOException;
}
