package com.ivonix.pulse.ontology.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

/**
 * Human/API abuse limiter. Machine ingress is authenticated by device credentials and has
 * its own admission controls; applying a 100/minute IP bucket to it would make horizontal
 * scaling and the 10k msg/s machine target structurally impossible.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {
  private final int capacity; private final int maxClients;
  private final Map<String,Bucket> buckets=Collections.synchronizedMap(new LinkedHashMap<>(256,.75f,true){
    protected boolean removeEldestEntry(Map.Entry<String,Bucket> e){return size()>maxClients;}
  });
  public RateLimitingFilter(@Value("${PULSE_RATE_LIMIT_PER_MINUTE:100}") int capacity,@Value("${PULSE_RATE_LIMIT_MAX_CLIENTS:10000}") int maxClients){
    if(capacity<1||capacity>10000||maxClients<100) throw new IllegalArgumentException("Invalid rate-limit configuration");
    this.capacity=capacity;this.maxClients=maxClients;
  }
  @Override protected boolean shouldNotFilter(HttpServletRequest req){
    return req.getRequestURI().startsWith("/api/v1/device-gateway/");
  }
  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
    String key=req.getRemoteAddr();Bucket b=buckets.computeIfAbsent(key,k->newBucket());
    if(!b.tryConsume(1)){res.setStatus(429);res.setHeader("Retry-After","60");res.setContentType("application/json");res.getWriter().write("{\"error\":\"rate_limit_exceeded\"}");return;}
    chain.doFilter(req,res);
  }
  private Bucket newBucket(){return Bucket.builder().addLimit(Bandwidth.classic(capacity,Refill.greedy(capacity,Duration.ofMinutes(1)))).build();}
}
