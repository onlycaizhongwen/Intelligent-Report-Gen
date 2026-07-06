package com.company.report.shared.api;

import java.util.List;

public record SseEvent(
        String type,
        String taskId,
        String content,
        String stage,
        List<Object> references,
        Double progress,
        String errorCode,
        String traceId
) {
    public static SseEvent stage(Object taskId, String stage, String content, double progress) {
        return new SseEvent("stage", String.valueOf(taskId), content, stage, List.of(), progress, null, null);
    }

    public static SseEvent delta(Object taskId, String content, String stage, double progress) {
        return new SseEvent("delta", String.valueOf(taskId), content, stage, List.of(), progress, null, null);
    }

    public static SseEvent references(Object taskId, List<Object> references, String traceId) {
        return new SseEvent("references", String.valueOf(taskId), "", "writing", references, null, null, traceId);
    }

    public static SseEvent error(Object taskId, String errorCode, String content, String traceId) {
        return new SseEvent("error", String.valueOf(taskId), content, null, List.of(), null, errorCode, traceId);
    }

    public static SseEvent done(Object taskId) {
        return new SseEvent("done", String.valueOf(taskId), "completed", "export", List.of(), 1.0, null, null);
    }
}
