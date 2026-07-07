package com.company.report.rule.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class BasicRuleApprovalSupplementAttachmentInspector implements RuleApprovalSupplementAttachmentInspector {
    private static final String ENGINE_NAME = "basic_attachment_content_inspector";
    private static final String EICAR_SIGNATURE = "EICAR-STANDARD-ANTIVIRUS-TEST-FILE";
    private static final long MAX_ARCHIVE_INSPECTION_BYTES = 10L * 1024 * 1024;
    private final Optional<RuleApprovalSupplementAttachmentAntivirusScanner> antivirusScanner;

    public BasicRuleApprovalSupplementAttachmentInspector() {
        this(Optional.empty());
    }

    public BasicRuleApprovalSupplementAttachmentInspector(RuleApprovalSupplementAttachmentAntivirusScanner antivirusScanner) {
        this(Optional.ofNullable(antivirusScanner));
    }

    @Autowired
    public BasicRuleApprovalSupplementAttachmentInspector(Optional<RuleApprovalSupplementAttachmentAntivirusScanner> antivirusScanner) {
        this.antivirusScanner = antivirusScanner == null ? Optional.empty() : antivirusScanner;
    }

    @Override
    public InspectionResult inspect(MultipartFile file, String contentType, String fileName) throws IOException {
        byte[] bytes = file.getBytes();
        if (containsAscii(bytes, EICAR_SIGNATURE)) {
            return InspectionResult.rejected(
                    "malware_signature_detected",
                    "known antivirus test signature detected"
            );
        }
        if (!matchesDeclaredContentType(bytes, contentType)) {
            return InspectionResult.rejected(
                    "content_signature_mismatch",
                    "file content does not match declared content type"
            );
        }
        if (isImageContentType(contentType) && containsImageActiveContentMarker(bytes)) {
            return InspectionResult.rejected(
                    "image_active_content_detected",
                    "image evidence contains active content markers"
            );
        }
        if (isOfficeOpenXmlContentType(contentType)) {
            InspectionResult archiveInspection = inspectOfficeArchive(bytes);
            if (!archiveInspection.accepted()) {
                return archiveInspection;
            }
        }
        if (antivirusScanner.isPresent()) {
            InspectionResult antivirusInspection = antivirusScanner.get().scan(bytes, contentType, fileName);
            if (!antivirusInspection.accepted()) {
                return antivirusInspection;
            }
        }
        return InspectionResult.passed();
    }

    @Override
    public String engineName() {
        return ENGINE_NAME;
    }

    private static boolean containsAscii(byte[] bytes, String signature) {
        return new String(bytes, StandardCharsets.US_ASCII).contains(signature);
    }

    private static boolean matchesDeclaredContentType(byte[] bytes, String contentType) {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "application/pdf" -> startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "image/jpeg" -> bytes.length >= 3
                    && unsigned(bytes[0]) == 0xFF
                    && unsigned(bytes[1]) == 0xD8
                    && unsigned(bytes[2]) == 0xFF;
            case "image/png" -> startsWith(bytes, new byte[] {
                    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
            });
            case "application/msword", "application/vnd.ms-excel" -> startsWith(bytes, new byte[] {
                    (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
            });
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    startsWith(bytes, "PK".getBytes(StandardCharsets.US_ASCII));
            default -> true;
        };
    }

    private static boolean isOfficeOpenXmlContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(normalized)
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(normalized);
    }

    private static boolean isImageContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return "image/jpeg".equals(normalized) || "image/png".equals(normalized);
    }

    private static boolean containsImageActiveContentMarker(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
        return content.contains("<script")
                || content.contains("<svg")
                || content.contains("<html")
                || content.contains("javascript:")
                || content.contains("onload=")
                || content.contains("<?php");
    }

    private static InspectionResult inspectOfficeArchive(byte[] bytes) throws IOException {
        if (hasEncryptedZipEntryFlag(bytes)) {
            return InspectionResult.rejected(
                    "encrypted_archive_unsupported",
                    "office archive declares encrypted entries"
            );
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName() == null ? "" : entry.getName().toLowerCase(Locale.ROOT);
                if (entryName.endsWith("vbaproject.bin")) {
                    return InspectionResult.rejected(
                            "macro_payload_detected",
                            "office archive contains a macro payload"
                    );
                }
                InspectionResult entryInspection = inspectEntryContent(zip, entryName);
                if (!entryInspection.accepted()) {
                    return entryInspection;
                }
                zip.closeEntry();
            }
        }
        return InspectionResult.passed();
    }

    private static boolean hasEncryptedZipEntryFlag(byte[] bytes) {
        for (int index = 0; index < bytes.length - 8; index++) {
            if (matchesSignature(bytes, index, 0x50, 0x4B, 0x03, 0x04)
                    && hasEncryptionFlag(bytes, index + 6)) {
                return true;
            }
            if (matchesSignature(bytes, index, 0x50, 0x4B, 0x01, 0x02)
                    && hasEncryptionFlag(bytes, index + 8)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesSignature(byte[] bytes, int offset, int first, int second, int third, int fourth) {
        return unsigned(bytes[offset]) == first
                && unsigned(bytes[offset + 1]) == second
                && unsigned(bytes[offset + 2]) == third
                && unsigned(bytes[offset + 3]) == fourth;
    }

    private static boolean hasEncryptionFlag(byte[] bytes, int offset) {
        return offset < bytes.length && (unsigned(bytes[offset]) & 0x01) == 0x01;
    }

    private static InspectionResult inspectEntryContent(ZipInputStream zip, String entryName) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        long inspectedBytes = 0L;
        int read;
        while ((read = zip.read(buffer)) != -1) {
            inspectedBytes += read;
            if (inspectedBytes > MAX_ARCHIVE_INSPECTION_BYTES) {
                return InspectionResult.rejected(
                        "archive_expansion_limit_exceeded",
                        "office archive expanded beyond the inspection limit"
                );
            }
            output.write(buffer, 0, read);
        }
        if (containsAscii(output.toByteArray(), EICAR_SIGNATURE)) {
            return InspectionResult.rejected(
                    "malware_signature_detected",
                    "known antivirus test signature detected inside office archive"
            );
        }
        if (isRelationshipEntry(entryName) && containsExternalRelationship(output.toByteArray())) {
            return InspectionResult.rejected(
                    "external_relationship_detected",
                    "office archive contains an external relationship"
            );
        }
        return InspectionResult.passed();
    }

    private static boolean isRelationshipEntry(String entryName) {
        return entryName != null && entryName.endsWith(".rels");
    }

    private static boolean containsExternalRelationship(byte[] bytes) {
        String relationshipXml = new String(bytes, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return relationshipXml.contains("targetmode=\"external\"")
                || relationshipXml.contains("targetmode='external'")
                || relationshipXml.contains("target=\"http://")
                || relationshipXml.contains("target='http://")
                || relationshipXml.contains("target=\"https://")
                || relationshipXml.contains("target='https://");
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }
}
