package com.company.report.rule.application;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface RuleApprovalSupplementAttachmentInspector {
    InspectionResult inspect(MultipartFile file, String contentType, String fileName) throws IOException;

    String engineName();

    record InspectionResult(boolean accepted, String rejectionReason, String message) {
        public static InspectionResult passed() {
            return new InspectionResult(true, null, null);
        }

        public static InspectionResult rejected(String rejectionReason, String message) {
            return new InspectionResult(false, rejectionReason, message);
        }
    }
}
