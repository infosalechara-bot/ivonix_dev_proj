package com.ivonix.pulse.ontology.finops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
public class FinOpsService {
  private final JdbcTemplate jdbc; private final ObjectMapper mapper;
  public FinOpsService(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
  @Transactional public void attribute(UUID orgId,String service,String resourceType,double quantity,String unit,Map<String,Object> tags){if(quantity<0)throw new IllegalArgumentException("quantity must be non-negative");Double rate=jdbc.query("select unit_cost_usd from public.finops_rate_card where service=? and resource_type=? and unit=? and effective_from<=current_date and(effective_to is null or effective_to>=current_date) order by effective_from desc limit 1",rs->rs.next()?rs.getDouble(1):null,service,resourceType,unit);if(rate==null)throw new IllegalArgumentException("No active rate-card entry");jdbc.update("insert into public.finops_cost_events(organization_id,service,resource_type,quantity,unit,unit_cost_usd,total_cost_usd,tags) values(?,?,?,?,?,?,?,?::jsonb)",orgId,service,resourceType,quantity,unit,rate,quantity*rate,toJson(tags));}
  @Scheduled(cron="0 30 4 * * *") @Transactional public void rollupProfitability(){LocalDate d=LocalDate.now().minusDays(1);if(!tableExists("organizations"))return;boolean billing=tableExists("bill_invoices");List<Map<String,Object>> orgs=jdbc.queryForList("select id from public.organizations");for(Map<String,Object> o:orgs){UUID org=(UUID)o.get("id");Double rev=billing?nullable("select coalesce(sum(total),0) from public.bill_invoices where organization_id=? and issued_at::date=?",org,d):0d;Double cogs=nullable("select coalesce(sum(total_cost_usd),0) from public.finops_cost_events where organization_id=? and event_time::date=?",org,d);jdbc.update("insert into public.finops_tenant_profitability(organization_id,period_start,period_end,revenue_usd,cogs_usd) values(?,?,?,?,?) on conflict(organization_id,period_start,period_end) do update set revenue_usd=excluded.revenue_usd,cogs_usd=excluded.cogs_usd",org,d,d,rev==null?0:rev,cogs==null?0:cogs);}}
  @Scheduled(fixedDelay=6*3600*1000) @Transactional public void detectAnomalies(){List<Map<String,Object>> rows=jdbc.queryForList("with daily as(select organization_id,event_time::date d,sum(total_cost_usd) cost from public.finops_cost_events where event_time>now()-interval '30 days' group by organization_id,event_time::date),agg as(select organization_id,avg(cost) mean,stddev_pop(cost) sd,max(case when d=current_date-1 then cost end) latest from daily group by organization_id) select * from agg where latest is not null and mean>0 and latest>mean+3*coalesce(sd,0)");for(Map<String,Object> r:rows){double mean=((Number)r.get("mean")).doubleValue(),latest=((Number)r.get("latest")).doubleValue();jdbc.update("insert into public.finops_anomalies(organization_id,expected_usd,observed_usd,deviation_pct) values(?,?,?,?)",r.get("organization_id"),mean,latest,(latest-mean)/mean*100);}}
  public List<Map<String,Object>> profitability(UUID orgId){return jdbc.queryForList("select * from public.finops_tenant_profitability where organization_id=? order by period_start desc limit 90",orgId);}
  public List<Map<String,Object>> anomalies(UUID orgId){return jdbc.queryForList("select * from public.finops_anomalies where organization_id=? and resolved_at is null order by detected_at desc limit 100",orgId);}
  public List<Map<String,Object>> costs(UUID orgId){return jdbc.queryForList("select * from public.finops_cost_events where organization_id=? order by event_time desc limit 500",orgId);}
  private boolean tableExists(String name){return Boolean.TRUE.equals(jdbc.queryForObject("select to_regclass(?) is not null",Boolean.class,"public."+name));}
  private Double nullable(String q,Object... a){List<Double>x=jdbc.query(q,(rs,n)->rs.getDouble(1),a);return x.isEmpty()?null:x.get(0);}
  private String toJson(Object o){try{return mapper.writeValueAsString(o==null?Map.of():o);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
}
