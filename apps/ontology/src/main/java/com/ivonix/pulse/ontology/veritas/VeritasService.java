package com.ivonix.pulse.ontology.veritas;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class VeritasService {
  private final JdbcTemplate jdbc;
  public VeritasService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Transactional
  public UUID createSession(SessionRequest r, UUID userId) {
    UUID id=UUID.randomUUID();
    requireMember(r.organizationId());
    jdbc.update("insert into public.veritas_sessions(id,organization_id,title,description,created_by) values(?,?,?,?,?)",id,r.organizationId(),r.title(),r.description(),userId);
    return id;
  }

  @Transactional
  public UUID addEvidence(UUID sessionId, EvidenceRequest r, UUID userId) {
    UUID org=orgForSession(sessionId); requireMember(org);
    if (!Set.of("audio","video","text").contains(r.evidenceType())) throw new IllegalArgumentException("Unsupported evidence type");
    if ("text".equals(r.evidenceType()) && (r.textContent()==null || r.textContent().isBlank())) throw new IllegalArgumentException("Text evidence requires textContent");
    UUID id=UUID.randomUUID();
    jdbc.update("insert into public.veritas_evidence(id,session_id,evidence_type,file_path,text_content,duration_seconds,uploaded_by) values(?,?,?,?,?,?,?)",id,sessionId,r.evidenceType(),r.filePath(),r.textContent(),r.durationSeconds(),userId);
    return id;
  }

  public List<Map<String,Object>> results(UUID sessionId, UUID userId) { requireMember(orgForSession(sessionId)); return jdbc.queryForList("select id,evidence_id,session_id,confidence,summary,created_at from public.veritas_analysis where session_id=? order by created_at desc",sessionId); }
  public List<Map<String,Object>> markers(UUID analysisId, UUID userId) { UUID org=jdbc.queryForObject("select s.organization_id from public.veritas_analysis a join public.veritas_sessions s on s.id=a.session_id where a.id=?",UUID.class,analysisId); requireMember(org); return jdbc.queryForList("select * from public.veritas_markers where analysis_id=? order by start_time nulls last, created_at",analysisId); }

  private UUID orgForSession(UUID id){ return jdbc.queryForObject("select organization_id from public.veritas_sessions where id=?",UUID.class,id); }
  private void requireMember(UUID org){ Integer n=jdbc.queryForObject("select count(*) from public.organization_members where organization_id=? and user_id=?",Integer.class,org,currentUser()); if(n==null||n<1) throw new SecurityException("Organization membership required"); }
  private UUID currentUser(){ return VeritasRequestContext.userId(); }

  public record SessionRequest(UUID organizationId,String title,String description) {}
  public record EvidenceRequest(String evidenceType,String filePath,String textContent,Double durationSeconds) {}
}
