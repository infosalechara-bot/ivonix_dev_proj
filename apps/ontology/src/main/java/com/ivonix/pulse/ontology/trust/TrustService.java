package com.ivonix.pulse.ontology.trust;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class TrustService {
  private final JdbcTemplate jdbc; private final ObjectMapper mapper;
  public TrustService(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
  @Transactional public void recordSignal(UUID userId,UUID deviceId,String type,double score,Map<String,Object> details){if(score<0||score>1)throw new IllegalArgumentException("score must be 0..1");jdbc.update("insert into public.trust_signals(user_id,device_id,signal_type,score,details) values(?,?,?,?,?::jsonb)",userId,deviceId,type,score,toJson(details));recomputeRisk(userId);evaluateRules(userId,type,score);}
  @Transactional public void recomputeRisk(UUID userId){Double a=jdbc.queryForObject("select coalesce(sum(score*exp(-extract(epoch from(now()-created_at))/86400)),0) from public.trust_signals where user_id=? and created_at>now()-interval '24 hours'",Double.class,userId);double score=Math.min(100,(a==null?0:a)*10);String level=score>80?"blocked":score>60?"high":score>30?"medium":"low";jdbc.update("insert into public.trust_risk_profiles(user_id,risk_score,risk_level,last_reviewed_at) values(?,?,?,now()) on conflict(user_id) do update set risk_score=excluded.risk_score,risk_level=excluded.risk_level,last_reviewed_at=now()",userId,score,level);}
  private void evaluateRules(UUID userId,String type,double score){List<Map<String,Object>> rules=jdbc.queryForList("select id,name,condition,action from public.trust_rules where signal_type=? and enabled=true order by priority,id",type);for(Map<String,Object> r:rules){Map<String,Object> c=parse(String.valueOf(r.get("condition")));if(!(c.get("value") instanceof Number))continue;double v=((Number)c.get("value")).doubleValue();boolean hit=switch(String.valueOf(c.get("op"))){case ">"->score>v;case ">="->score>=v;case "<"->score<v;case "<="->score<=v;default->false;};if(hit){Map<String,Object> action=parse(String.valueOf(r.get("action")));applyAction(userId,String.valueOf(action.get("type")),String.valueOf(r.get("name")));}}}
  private void applyAction(UUID userId,String action,String reason){switch(action){case "require_mfa"->jdbc.update("update public.trust_risk_profiles set requires_mfa=true where user_id=?",userId);case "block"->jdbc.update("insert into public.trust_blocks(subject_type,subject_value,reason) values('user',?,?) on conflict(subject_type,subject_value) do update set reason=excluded.reason,blocked_at=now()",userId.toString(),reason);case "challenge","notify_analyst"->{}default->throw new IllegalArgumentException("Unsupported trust action");}}
  @Scheduled(fixedDelay=60_000) public void detectFraudPatterns(){if(tableExists("pay_methods")){List<Map<String,Object>> p=jdbc.queryForList("select user_id,count(*) cnt from public.pay_methods where created_at>now()-interval '1 hour' group by user_id having count(*)>5");for(Map<String,Object> s:p)recordSignal((UUID)s.get("user_id"),null,"payment_velocity",Math.min(1,((Number)s.get("cnt")).doubleValue()/10),Map.of("count",s.get("cnt")));}if(tableExists("ip_geolocation_cache")){List<Map<String,Object>> g=jdbc.queryForList("select user_id,count(distinct country) countries from public.audit_logs a join public.ip_geolocation_cache c on a.ip_address=c.ip where a.action='login' and a.created_at>now()-interval '1 hour' group by user_id having count(distinct country)>1");for(Map<String,Object> s:g)recordSignal((UUID)s.get("user_id"),null,"login_geo_anomaly",0.9,Map.of("countries",s.get("countries")));}}
  @Transactional public UUID reportContent(UUID reporter,String type,String target,String reason){UUID id=UUID.randomUUID();jdbc.update("insert into public.trust_content_reports(id,reporter_id,target_type,target_id,reason) values(?,?,?,?,?)",id,reporter,type,target,reason);return id;}
  public List<Map<String,Object>> highRisk(){return jdbc.queryForList("select user_id,risk_level,risk_score,requires_mfa,last_reviewed_at from public.trust_risk_profiles where risk_level in('high','blocked') order by risk_score desc limit 100");}
  public List<Map<String,Object>> openReports(){return jdbc.queryForList("select * from public.trust_content_reports where status='open' order by created_at desc limit 100");}
  private boolean tableExists(String name){return Boolean.TRUE.equals(jdbc.queryForObject("select to_regclass(?) is not null",Boolean.class,"public."+name));}
  private Map<String,Object> parse(String s){try{return mapper.readValue(s,Map.class);}catch(Exception e){return Map.of();}}
  private String toJson(Object o){try{return mapper.writeValueAsString(o==null?Map.of():o);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
}
