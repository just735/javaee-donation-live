package com.javaee.donation.viewer.support;

import com.javaee.donation.common.api.ApiResponse;

public final class ViewerApiResponses {

    private ViewerApiResponses() {
    }

    public static <T> ApiResponse<T> success(String traceId, String code, String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code(code)
                .message(message)
                .traceId(traceId)
                .data(data)
                .build();
    }
}
