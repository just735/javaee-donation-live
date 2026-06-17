package com.javaee.donation.viewer.config;

import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.viewer.constant.ViewerConstants;
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

/**
 * 观众服务本地链路过滤器，配合公共 TraceIdFilter 记录业务入口日志。
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

        String traceId = TraceContext.getTraceId();
        log.info("[{}][{}] viewer api enter, method={}, path={}, query={}",
                traceId, ViewerConstants.SERVICE_NAME,
                request.getMethod(), request.getRequestURI(), request.getQueryString());

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
            log.info("[{}][{}] viewer api exit, method={}, path={}, status={}, costMs={}",
                    traceId, ViewerConstants.SERVICE_NAME,
                    request.getMethod(), request.getRequestURI(), response.getStatus(),
                    System.currentTimeMillis() - start);
        } catch (Exception exception) {
            log.error("[{}][{}] viewer api error, method={}, path={}, error={}",
                    traceId, ViewerConstants.SERVICE_NAME,
                    request.getMethod(), request.getRequestURI(), exception.getMessage());
            throw exception;
        }
    }
}
