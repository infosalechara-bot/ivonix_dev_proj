package com.ivonix.pulse.ontology.security;

import com.ivonix.pulse.ontology.auth.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtService jwt;
  public JwtAuthenticationFilter(JwtService jwt){this.jwt=jwt;}
  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
    String h=req.getHeader("Authorization");
    if(h!=null && h.startsWith("Bearer ")) try {
      Claims c=jwt.claims(h.substring(7));
      var auth=new UsernamePasswordAuthenticationToken(jwt.userId(c),null,List.of(new SimpleGrantedAuthority("ROLE_USER")));
      auth.setDetails(jwt.organizationId(c)); SecurityContextHolder.getContext().setAuthentication(auth);
    } catch(Exception ignored) { SecurityContextHolder.clearContext(); }
    chain.doFilter(req,res);
  }
}
