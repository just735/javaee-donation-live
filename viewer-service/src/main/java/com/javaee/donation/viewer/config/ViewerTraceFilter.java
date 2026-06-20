package com.javaee.donation.viewer.config;

import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.viewer.constant.ViewerConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * 观众服务本地链路过滤器，配合公共 TraceIdFilter 记录业务入口日志与请求体。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ViewerTraceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ViewerTraceFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/viewers")) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        String traceId = TraceContext.getTraceId();
        log.info("[{}][{}] viewer api enter, method={}, path={}, query={}",
                traceId, ViewerConstants.SERVICE_NAME,
                request.getMethod(), request.getRequestURI(), request.getQueryString());

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(wrappedRequest, response);
            logRequestBody(traceId, wrappedRequest);
            log.info("[{}][{}] viewer api exit, method={}, path={}, status={}, costMs={}",
                    traceId, ViewerConstants.SERVICE_NAME,
                    request.getMethod(), request.getRequestURI(), response.getStatus(),
                    System.currentTimeMillis() - start);
        } catch (Exception exception) {
            logRequestBody(traceId, wrappedRequest);
            log.error("[{}][{}] viewer api error, method={}, path={}, error={}",
                    traceId, ViewerConstants.SERVICE_NAME,
                    request.getMethod(), request.getRequestURI(), exception.getMessage());
            throw exception;
        }
    }

    private void logRequestBody(String traceId, ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length == 0) {
            return;
        }
        String body = new String(content, StandardCharsets.UTF_8);
        log.info("[{}][{}] viewer api request body={}",
                traceId, ViewerConstants.SERVICE_NAME, body);
    }
}
