package com.ivonix.pulse.ontology.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitingFilterTest {
    @Test
    void machineIngressBypassesHumanIpLimiter() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(1, 100);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/device-gateway/ingest");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void humanTrafficIsRateLimited() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(1, 100);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/v1/security/health");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, chain);

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/v1/security/health");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertEquals(429, secondResponse.getStatus());
    }
}
