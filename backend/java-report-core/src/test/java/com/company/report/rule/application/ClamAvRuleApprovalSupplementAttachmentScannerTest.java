package com.company.report.rule.application;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ClamAvRuleApprovalSupplementAttachmentScannerTest {

    @Test
    void disabledScannerPassesWithoutConnectingToExternalService() throws IOException {
        try (ServerSocket server = new ServerSocket(0)) {
            ClamAvRuleApprovalSupplementAttachmentScanner scanner = new ClamAvRuleApprovalSupplementAttachmentScanner(
                    new RuleApprovalSupplementAttachmentExternalAvProperties(
                            false,
                            "127.0.0.1",
                            server.getLocalPort(),
                            200,
                            200,
                            1024
                    )
            );

            RuleApprovalSupplementAttachmentInspector.InspectionResult result = scanner.scan(
                    "plain evidence".getBytes(StandardCharsets.UTF_8),
                    "text/plain",
                    "evidence.txt"
            );

            assertThat(result.accepted()).isTrue();
        }
    }

    @Test
    void enabledScannerStreamsFileToClamAvAndRejectsFoundResponse() throws Exception {
        byte[] payload = "malicious evidence".getBytes(StandardCharsets.UTF_8);
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<byte[]> receivedPayload = CompletableFuture.supplyAsync(() -> acceptClamAvPayload(server));
            ClamAvRuleApprovalSupplementAttachmentScanner scanner = new ClamAvRuleApprovalSupplementAttachmentScanner(
                    new RuleApprovalSupplementAttachmentExternalAvProperties(
                            true,
                            "127.0.0.1",
                            server.getLocalPort(),
                            1000,
                            1000,
                            1024
                    )
            );

            RuleApprovalSupplementAttachmentInspector.InspectionResult result = scanner.scan(
                    payload,
                    "text/plain",
                    "evidence.txt"
            );

            assertThat(receivedPayload.get(2, TimeUnit.SECONDS)).isEqualTo(payload);
            assertThat(result.accepted()).isFalse();
            assertThat(result.rejectionReason()).isEqualTo("malware_detected_by_external_av");
            assertThat(result.engineName()).isEqualTo("clamav_instream");
        }
    }

    private static byte[] acceptClamAvPayload(ServerSocket server) {
        try (Socket socket = server.accept()) {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            byte[] command = input.readNBytes("zINSTREAM\0".length());
            if (!"zINSTREAM\0".equals(new String(command, StandardCharsets.US_ASCII))) {
                throw new IllegalStateException("unexpected ClamAV command");
            }
            int chunkSize = input.readInt();
            byte[] payload = input.readNBytes(chunkSize);
            int terminator = input.readInt();
            if (terminator != 0) {
                throw new IllegalStateException("missing ClamAV stream terminator");
            }
            socket.getOutputStream().write("stream: Eicar-Test-Signature FOUND\0".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return payload;
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }
}
