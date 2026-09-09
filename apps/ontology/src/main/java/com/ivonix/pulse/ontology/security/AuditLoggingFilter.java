package com.ivonix.pulse.ontology.security;

import com.ivonix.pulse.ontology.auth.AuditService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
public class AuditLoggingFilter extends OncePerRequestFilter {
  private final AuditService audit;
  public AuditLoggingFilter(AuditService audit){this.audit=audit;}
  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
    chain.doFilter(req,res);
    if(res.getStatus()<400 && req.getRequestURI().startsWith("/api/v1/") && !req.getRequestURI().startsWith("/api/v1/auth/")){
      Authentication a=SecurityContextHolder.getContext().getAuthentication();
      if(a!=null && a.getPrincipal() instanceof UUID user) audit.record(user,req.getMethod().toLowerCase()+" "+req.getRequestURI(),"http",null,req);
    }
  }
}
