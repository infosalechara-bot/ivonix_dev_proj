package com.ivonix.pulse.ontology.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Prevents a signed JWT from selecting an organization it is not actually a member of.
 * The organization claim is treated as a routing hint, not an authorization grant.
 */
@Component
public class OrganizationMembershipFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;

    public OrganizationMembershipFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/device-gateway/")) {
            chain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getDetails() instanceof UUID orgId) {
            try {
                UUID userId = UUID.fromString(authentication.getName());
                Integer members = jdbc.queryForObject(
                        "select count(*) from public.organization_members where organization_id=? and user_id=?",
                        Integer.class, orgId, userId);
                if (members == null || members < 1) {
                    SecurityContextHolder.clearContext();
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Organization membership required");
                    return;
                }
            } catch (IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid authentication identity");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
