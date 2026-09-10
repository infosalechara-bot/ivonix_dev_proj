package com.ivonix.pulse.ontology.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrganizationMembershipFilterTest {
    @Test
    void rejectsAuthenticatedUserWhoseJwtOrganizationIsNotMembership() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OrganizationMembershipFilter filter = new OrganizationMembershipFilter(jdbc);
        UUID user = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken(user.toString(), null, java.util.List.of());
        auth.setDetails(org);
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(org), eq(user))).thenReturn(0);
        var request = new MockHttpServletRequest("GET", "/api/v1/ontology/search");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void allowsAuthenticatedMember() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OrganizationMembershipFilter filter = new OrganizationMembershipFilter(jdbc);
        UUID user = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken(user.toString(), null, java.util.List.of());
        auth.setDetails(org);
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(org), eq(user))).thenReturn(1);
        var request = new MockHttpServletRequest("GET", "/api/v1/ontology/search");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotApplyHumanMembershipCheckToDeviceIngress() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OrganizationMembershipFilter filter = new OrganizationMembershipFilter(jdbc);
        var request = new MockHttpServletRequest("POST", "/api/v1/device-gateway/messages");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(jdbc);
        verify(chain).doFilter(request, response);
    }
}
