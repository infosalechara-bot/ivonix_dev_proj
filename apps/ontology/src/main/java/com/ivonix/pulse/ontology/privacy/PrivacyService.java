package com.ivonix.pulse.ontology.privacy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class PrivacyService {
  private static final Set<String> PURPOSES=Set.of("marketing","analytics","ai_training");
  private static final Set<String> DSAR_TYPES=Set.of("access","portability","erasure","rectification","restriction");
  private final JdbcTemplate jdbc; private final ObjectMapper mapper;
  public PrivacyService(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}

  @Transactional
  public void recordConsent(UUID userId,String purpose,boolean granted,String version,String ip,String ua){
    requirePurpose(purpose);
    jdbc.update("insert into public.privacy_consents(user_id,purpose,granted,granted_at,revoked_at,version,ip_address,user_agent) values(?,?,?,?,?,?,?::inet,?)",
      userId,purpose,granted,Instant.now(),granted?null:Instant.now(),version,ip,ua);
  }
  public List<Map<String,Object>> consents(UUID userId){return jdbc.queryForList("select id,purpose,granted,granted_at,revoked_at,version from public.privacy_consents where user_id=? order by granted_at desc",userId);}
  public boolean hasConsent(UUID userId,String purpose){requirePurpose(purpose); List<Boolean> v=jdbc.query("select granted from public.privacy_consents where user_id=? and purpose=? order by granted_at desc limit 1",(rs,n)->rs.getBoolean(1),userId,purpose); return !v.isEmpty()&&v.get(0);}

  @Transactional
  public UUID submitDsar(UUID userId,String type){if(!DSAR_TYPES.contains(type))throw new IllegalArgumentException("Unsupported DSAR type"); UUID id=UUID.randomUUID(); jdbc.update("insert into public.privacy_dsar_requests(id,user_id,request_type,status) values(?,?,?,'verifying')",id,userId,type); jdbc.update("update public.privacy_dsar_requests set verified_at=now(),status='in_progress' where id=?",id); return id;}
  public List<Map<String,Object>> dsars(UUID userId){return jdbc.queryForList("select id,request_type,status,submitted_at,verified_at,completed_at,rejection_reason,result_path,sla_due_at from public.privacy_dsar_requests where user_id=? order by submitted_at desc",userId);}

  @Transactional
  public Map<String,Object> generateAccessExport(UUID userId,UUID dsarId){ensureOwnedDsar(userId,dsarId); Map<String,Object> data=new LinkedHashMap<>();
    data.put("user",jdbc.queryForMap("select id,email,created_at from auth.users where id=?",userId));
    data.put("consents",consents(userId));
    data.put("dsar_requests",dsars(userId));
    data.put("trust_signals",safeRows("trust_signals","user_id",userId));
    data.put("risk_profile",safeRows("trust_risk_profiles","user_id",userId));
    data.put("content_reports",safeRows("trust_content_reports","reporter_id",userId));
    String json=toJson(data); jdbc.update("update public.privacy_dsar_requests set status='completed',completed_at=now(),result_path=?,result_payload=?::jsonb where id=?","inline-json:"+dsarId,json,dsarId); return data;
  }

  @Transactional
  public UUID executeErasure(UUID userId,UUID dsarId,UUID confirmationId){ensureOwnedDsar(userId,dsarId); if(confirmationId==null)throw new SecurityException("Founder confirmation required for erasure");
    Integer approved=jdbc.queryForObject("select count(*) from public.executive_confirmations where id=? and requested_by=? and status='approved' and consumed_at is null and expires_at>now()",Integer.class,confirmationId,userId);
    if(approved==null||approved!=1)throw new SecurityException("Valid founder confirmation required");
    jdbc.update("update public.executive_confirmations set consumed_at=now(),consumed_by=? where id=? and requested_by=? and status='approved' and consumed_at is null and expires_at>now()",userId,confirmationId,userId);
    UUID job=UUID.randomUUID(); jdbc.update("insert into public.privacy_erasure_jobs(id,dsar_request_id,user_id) values(?,?,?)",job,dsarId,userId);
    List<Map<String,Object>> lineage=jdbc.queryForList("select source_table from public.privacy_data_lineage where data_category='pii'");
    Set<String> allowed=Set.of("profiles","trust_signals","trust_risk_profiles","trust_content_reports","privacy_consents"); List<String> processed=new ArrayList<>(); List<Map<String,String>> skipped=new ArrayList<>(); long deleted=0;
    for(Map<String,Object> row:lineage){String table=String.valueOf(row.get("source_table")); if(!allowed.contains(table)){skipped.add(Map.of("table",table,"reason","not_allowlisted"));continue;}
      Integer hold=jdbc.queryForObject("select count(*) from public.legal_holds where entity_type=? and (entity_id is null or entity_id=?)",Integer.class,table,userId.toString()); if(hold!=null&&hold>0){skipped.add(Map.of("table",table,"reason","legal_hold"));continue;}
      if(hasUserIdColumn(table)){int n=jdbc.update("delete from public."+table+" where user_id=?",userId);deleted+=n;processed.add(table+":"+n);} else skipped.add(Map.of("table",table,"reason","no_user_id_column"));
    }
    jdbc.update("update public.privacy_erasure_jobs set status='completed',completed_at=now(),rows_deleted=?,tables_processed=?::jsonb,tables_skipped=?::jsonb where id=?",deleted,toJson(processed),toJson(skipped),job);
    jdbc.update("update public.privacy_dsar_requests set status='completed',completed_at=now() where id=?",dsarId); return job;
  }

  private boolean hasUserIdColumn(String table){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from information_schema.columns where table_schema='public' and table_name=? and column_name='user_id')",Boolean.class,table));}
  private List<Map<String,Object>> safeRows(String table,String col,UUID id){return jdbc.queryForList("select * from public."+table+" where "+col+"=? limit 1000",id);}
  private void ensureOwnedDsar(UUID user,UUID id){Integer n=jdbc.queryForObject("select count(*) from public.privacy_dsar_requests where id=? and user_id=?",Integer.class,id,user);if(n==null||n!=1)throw new SecurityException("DSAR not found");}
  private void requirePurpose(String p){if(!PURPOSES.contains(p))throw new IllegalArgumentException("Unsupported consent purpose");}
  private String toJson(Object o){try{return mapper.writeValueAsString(o);}catch(JsonProcessingException e){throw new IllegalStateException("JSON serialization failed",e);}}
  public double privateCount(UUID userId,String metric,double epsilon){if(epsilon<=0||epsilon>10)throw new IllegalArgumentException("epsilon must be >0 and <=10"); if(!"trust_signals".equals(metric))throw new IllegalArgumentException("Metric not allowlisted"); Long raw=jdbc.queryForObject("select count(distinct id) from public.trust_signals where user_id=?",Long.class,userId); double u=new java.security.SecureRandom().nextDouble()-0.5; double noise=-(1/epsilon)*Math.signum(u)*Math.log(1-2*Math.abs(u));return Math.max(0,(raw==null?0:raw)+noise);}
}
