package com.javaee.donation.analytics.config;

import com.javaee.donation.analytics.exception.AnalyticsBusinessException;
import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.context.TraceContext;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AnalyticsExceptionHandler {

    @ExceptionHandler(AnalyticsBusinessException.class)
    public ApiResponse<Void> handleAnalyticsBusinessException(AnalyticsBusinessException exception) {
        return ApiResponse.fail(TraceContext.getTraceId(), exception.getCode(), exception.getMessage());
    }
}
