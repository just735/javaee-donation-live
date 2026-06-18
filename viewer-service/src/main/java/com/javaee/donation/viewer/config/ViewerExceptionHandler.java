package com.javaee.donation.viewer.config;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.viewer.exception.ViewerBusinessException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ViewerExceptionHandler {

    @ExceptionHandler(ViewerBusinessException.class)
    public ApiResponse<Void> handleViewerBusinessException(ViewerBusinessException exception) {
        return ApiResponse.fail(TraceContext.getTraceId(), exception.getCode(), exception.getMessage());
    }
}
