package com.javaee.donation.common.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private boolean success;
    private String code;
    private String message;
    private String traceId;
    private T data;

    public static <T> ApiResponse<T> success(String traceId, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code("SUCCESS")
                .message("ok")
                .traceId(traceId)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> fail(String traceId, String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .traceId(traceId)
                .build();
    }
}
