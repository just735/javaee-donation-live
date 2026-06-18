package com.javaee.donation.viewer.config;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.viewer.exception.ViewerBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ViewerExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ViewerExceptionHandler.class);

    @ExceptionHandler(ViewerBusinessException.class)
    public ApiResponse<Void> handleViewerBusinessException(ViewerBusinessException exception) {
        String traceId = TraceContext.getTraceId();
        log.warn("[{}] viewer business error, code={}, message={}",
                traceId, exception.getCode(), exception.getMessage());
        return ApiResponse.fail(traceId, exception.getCode(), exception.getMessage());
    }
}
