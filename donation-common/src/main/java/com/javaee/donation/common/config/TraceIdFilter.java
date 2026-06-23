package com.javaee.donation.common.config;

import com.javaee.donation.common.context.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TraceContext.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader(TraceContext.X_TRACE_ID_HEADER);
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceContext.newTraceId();
        }
        TraceContext.setTraceId(traceId);
        response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
        response.setHeader(TraceContext.X_TRACE_ID_HEADER, traceId);
        long start = System.currentTimeMillis();
        try {
            log.info("http request start, method={}, path={}, query={}, remoteIp={}, userAgent={}",
                    request.getMethod(), request.getRequestURI(), request.getQueryString(),
                    request.getRemoteAddr(), request.getHeader("User-Agent"));
            filterChain.doFilter(request, response);
            log.info("http request done, method={}, path={}, status={}, costMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), System.currentTimeMillis() - start);
        } catch (IOException | ServletException | RuntimeException exception) {
            log.warn("http request failed, method={}, path={}, status={}, costMs={}, error={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(),
                    System.currentTimeMillis() - start, exception.getMessage(), exception);
            throw exception;
        } finally {
            TraceContext.clear();
        }
    }
}
