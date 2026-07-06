package com.company.report.rule.application;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@Component
public class ClamAvRuleApprovalSupplementAttachmentScanner implements RuleApprovalSupplementAttachmentAntivirusScanner {
    private static final String ENGINE_NAME = "clamav_instream";
    private static final byte[] INSTREAM_COMMAND = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);
    private static final int CHUNK_SIZE = 8192;

    private final RuleApprovalSupplementAttachmentExternalAvProperties properties;

    public ClamAvRuleApprovalSupplementAttachmentScanner(RuleApprovalSupplementAttachmentExternalAvProperties properties) {
        this.properties = properties == null
                ? RuleApprovalSupplementAttachmentExternalAvProperties.disabled()
                : properties;
    }

    @Override
    public RuleApprovalSupplementAttachmentInspector.InspectionResult scan(byte[] fileBytes, String contentType, String fileName) throws IOException {
        if (!properties.enabled()) {
            return RuleApprovalSupplementAttachmentInspector.InspectionResult.passed();
        }
        byte[] bytes = fileBytes == null ? new byte[0] : fileBytes;
        if (bytes.length > properties.maxScanSizeBytes()) {
            return RuleApprovalSupplementAttachmentInspector.InspectionResult.rejected(
                    "external_av_scan_size_exceeded",
                    "external antivirus scan size limit exceeded",
                    ENGINE_NAME
            );
        }
        String response = scanWithClamAv(bytes);
        if (response.contains(" FOUND")) {
            return RuleApprovalSupplementAttachmentInspector.InspectionResult.rejected(
                    "malware_detected_by_external_av",
                    "external antivirus engine reported malware",
                    ENGINE_NAME
            );
        }
        if (response.contains(" OK")) {
            return RuleApprovalSupplementAttachmentInspector.InspectionResult.passed();
        }
        throw new IOException("unexpected external antivirus response: " + response);
    }

    private String scanWithClamAv(byte[] bytes) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(properties.host(), properties.port()), properties.connectTimeoutMillis());
            socket.setSoTimeout(properties.readTimeoutMillis());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.write(INSTREAM_COMMAND);
            int offset = 0;
            while (offset < bytes.length) {
                int length = Math.min(CHUNK_SIZE, bytes.length - offset);
                output.writeInt(length);
                output.write(bytes, offset, length);
                offset += length;
            }
            output.writeInt(0);
            output.flush();
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            byte[] buffer = new byte[256];
            int read;
            while ((read = socket.getInputStream().read(buffer)) != -1) {
                response.write(buffer, 0, read);
                if (containsTerminator(buffer, read)) {
                    break;
                }
            }
            return response.toString(StandardCharsets.US_ASCII).replace("\0", "").trim();
        }
    }

    private static boolean containsTerminator(byte[] buffer, int length) {
        for (int index = 0; index < length; index++) {
            if (buffer[index] == 0) {
                return true;
            }
        }
        return false;
    }
}
