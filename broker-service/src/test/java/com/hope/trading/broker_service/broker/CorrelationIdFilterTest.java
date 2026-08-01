package com.hope.trading.broker_service.broker;

import com.hope.trading.broker_service.broker.infrastructure.monitoring.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;

class CorrelationIdFilterTest {
    @Test void propagatesIncomingCorrelationIdAndCleansMdc() throws Exception {var request=new MockHttpServletRequest();request.addHeader(CorrelationIdFilter.HEADER,"corr-123");var response=new MockHttpServletResponse();jakarta.servlet.FilterChain chain=(req,res)->assertThat(MDC.get("correlationId")).isEqualTo("corr-123");new CorrelationIdFilter().doFilter(request,response,chain);assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("corr-123");assertThat(MDC.get("correlationId")).isNull();}
    @Test void createsCorrelationIdWhenAbsent() throws Exception {var response=new MockHttpServletResponse();new CorrelationIdFilter().doFilter(new MockHttpServletRequest(),response,new MockFilterChain());assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotBlank();}
}
