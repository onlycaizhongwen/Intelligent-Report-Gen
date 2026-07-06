package com.company.report.rule.application;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class BasicRuleApprovalSupplementAttachmentInspector implements RuleApprovalSupplementAttachmentInspector {
    private static final String ENGINE_NAME = "basic_attachment_content_inspector";
    private static final String EICAR_SIGNATURE = "EICAR-STANDARD-ANTIVIRUS-TEST-FILE";

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
        if (isOfficeOpenXmlContentType(contentType)) {
            InspectionResult archiveInspection = inspectOfficeArchive(bytes);
            if (!archiveInspection.accepted()) {
                return archiveInspection;
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

    private static InspectionResult inspectOfficeArchive(byte[] bytes) throws IOException {
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
                byte[] entryBytes = readEntry(zip);
                if (containsAscii(entryBytes, EICAR_SIGNATURE)) {
                    return InspectionResult.rejected(
                            "malware_signature_detected",
                            "known antivirus test signature detected inside office archive"
                    );
                }
                zip.closeEntry();
            }
        }
        return InspectionResult.passed();
    }

    private static byte[] readEntry(ZipInputStream zip) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = zip.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
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
