package com.javaee.donation.common.config;

import com.javaee.donation.common.context.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        TraceContext.setTraceId(traceId);
        response.setHeader("traceId", traceId);
        long start = System.currentTimeMillis();
        try {
            log.info("[{}] http request start, method={}, path={}, query={}",
                    traceId, request.getMethod(), request.getRequestURI(), request.getQueryString());
            filterChain.doFilter(request, response);
            log.info("[{}] http request done, method={}, path={}, status={}, costMs={}",
                    traceId, request.getMethod(), request.getRequestURI(), response.getStatus(),
                    System.currentTimeMillis() - start);
        } finally {
            TraceContext.clear();
        }
    }
}
