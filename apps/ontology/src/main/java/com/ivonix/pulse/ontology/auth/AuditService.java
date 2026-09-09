package com.ivonix.pulse.ontology.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class AuditService {
  private final JdbcTemplate jdbc;
  public AuditService(JdbcTemplate jdbc){this.jdbc=jdbc;}
  public void record(UUID userId,String action,String resourceType,String resourceId,HttpServletRequest request){
    jdbc.update("insert into public.audit_logs(user_id,action,resource_type,resource_id,ip_address,user_agent) values (?,?,?,?,cast(? as inet),?)",
      userId,action,resourceType,resourceId,request.getRemoteAddr(),request.getHeader("User-Agent"));
  }
}
