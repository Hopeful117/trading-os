package com.hope.trading.broker_service.broker.infrastructure.monitoring;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER="X-Correlation-ID";
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String received=request.getHeader(HEADER);
        String correlationId=received==null||received.isBlank()?UUID.randomUUID().toString():received.trim();
        response.setHeader(HEADER,correlationId);
        try(MDC.MDCCloseable ignored=MDC.putCloseable("correlationId",correlationId)){chain.doFilter(request,response);}
    }
}
