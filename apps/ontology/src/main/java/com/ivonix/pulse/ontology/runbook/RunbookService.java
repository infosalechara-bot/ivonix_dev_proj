package com.ivonix.pulse.ontology.runbook;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class RunbookService {
  private final JdbcTemplate jdbc; public RunbookService(JdbcTemplate jdbc){this.jdbc=jdbc;}
  public List<Map<String,Object>> incidents(UUID org){return jdbc.queryForList("select * from public.runbook_incidents where organization_id=? order by declared_at desc limit 100",org);}
  public List<Map<String,Object>> teams(UUID org){return jdbc.queryForList("select * from public.runbook_teams where organization_id=? order by name",org);}
  public List<Map<String,Object>> schedules(UUID org){return jdbc.queryForList("select s.* from public.runbook_schedules s join public.runbook_teams t on t.id=s.team_id where t.organization_id=? order by s.starts_at",org);}
  public List<Map<String,Object>> alerts(UUID org){return jdbc.queryForList("select * from public.runbook_alerts where org_id=? order by fired_at desc limit 200",org);}
  public List<Map<String,Object>> timeline(UUID org,UUID incident){requireIncident(org,incident);return jdbc.queryForList("select * from public.runbook_timeline where incident_id=? order by event_time,id",incident);}
  @Transactional public UUID declare(UUID org,UUID actor,String title,String severity,List<String> services,String impact){validateSeverity(severity);UUID id=UUID.randomUUID();jdbc.update("insert into public.runbook_incidents(id,organization_id,title,severity,declared_by,commander,affected_services,customer_impact) values(?,?,?,?,?,?,?,?)",id,org,title,severity,actor,actor,services==null?new String[0]:services.toArray(String[]::new),impact);timeline(org,id,actor,"declared",title);return id;}
  @Transactional public void addNote(UUID org,UUID incident,UUID actor,String type,String content){requireIncident(org,incident);if(!Set.of("note","action","escalation","resolved").contains(type))throw new IllegalArgumentException("Invalid timeline event type");timeline(org,incident,actor,type,content);}
  @Transactional public void updateStatus(UUID org,UUID incident,UUID actor,String status,String rootCause){requireIncident(org,incident);if(!Set.of("open","mitigated","resolved").contains(status))throw new IllegalArgumentException("Invalid incident status");String sql="update public.runbook_incidents set status=?,mitigated_at=case when ?='mitigated' and mitigated_at is null then now() else mitigated_at end,resolved_at=case when ?='resolved' then coalesce(resolved_at,now()) else resolved_at end,root_cause=coalesce(?,root_cause) where id=? and organization_id=?";jdbc.update(sql,status,status,status,rootCause,incident,org);timeline(org,incident,actor,"resolved","status="+status);}
  @Transactional public void acknowledgeAlert(UUID org,UUID alert,UUID actor){Integer n=jdbc.update("update public.runbook_alerts set status='acknowledged',acknowledged_at=now(),acknowledged_by=? where id=? and org_id=? and status='firing'",actor,alert,org);if(n!=1)throw new IllegalArgumentException("Alert not firing or not found");}
  @Transactional public UUID attachAlert(UUID org,UUID alert,UUID incident){requireIncident(org,incident);int n=jdbc.update("update public.runbook_alerts set incident_id=? where id=? and org_id=?",incident,alert,org);if(n!=1)throw new IllegalArgumentException("Alert not found");return incident;}
  private void timeline(UUID org,UUID incident,UUID actor,String type,String content){jdbc.update("insert into public.runbook_timeline(incident_id,event_type,actor_id,content) values(?,?,?,?)",incident,type,actor,content);}
  private void requireIncident(UUID org,UUID incident){Integer n=jdbc.queryForObject("select count(*) from public.runbook_incidents where id=? and organization_id=?",Integer.class,incident,org);if(n==null||n!=1)throw new SecurityException("Incident not found");}
  private void validateSeverity(String s){if(!Set.of("SEV1","SEV2","SEV3","SEV4").contains(s))throw new IllegalArgumentException("Invalid severity");}
}
