package com.javaee.donation.common.context;

import java.util.UUID;
import org.slf4j.MDC;

public final class TraceContext {

    public static final String TRACE_ID_HEADER = "traceId";
    public static final String X_TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID_KEY = "traceId";

    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void setTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            clear();
            return;
        }
        TRACE_ID_HOLDER.set(traceId);
        MDC.put(MDC_TRACE_ID_KEY, traceId);
    }

    public static String getTraceId() {
        return TRACE_ID_HOLDER.get();
    }

    public static String currentOrCreate() {
        String traceId = getTraceId();
        if (traceId == null || traceId.isBlank()) {
            traceId = newTraceId();
            setTraceId(traceId);
        }
        return traceId;
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static Runnable wrap(Runnable runnable) {
        String capturedTraceId = getTraceId();
        return () -> {
            String previousTraceId = getTraceId();
            try {
                if (capturedTraceId != null && !capturedTraceId.isBlank()) {
                    setTraceId(capturedTraceId);
                } else {
                    clear();
                }
                runnable.run();
            } finally {
                if (previousTraceId != null && !previousTraceId.isBlank()) {
                    setTraceId(previousTraceId);
                } else {
                    clear();
                }
            }
        };
    }

    public static void clear() {
        TRACE_ID_HOLDER.remove();
        MDC.remove(MDC_TRACE_ID_KEY);
    }
}
