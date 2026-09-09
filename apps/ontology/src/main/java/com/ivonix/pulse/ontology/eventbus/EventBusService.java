package com.ivonix.pulse.ontology.eventbus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
public class EventBusService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public EventBusService(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public UUID publish(String type, String source, Map<String,Object> payload, UUID org, String correlationId, UUID userId) {
        requireMember(org, userId);
        UUID typeId = jdbc.queryForObject("select id from public.event_types where name=?", UUID.class, type);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into public.events(id,event_type_id,organization_id,source_service,payload,correlation_id) values (?,?,?,?,?::jsonb,?)",
                    id,typeId,org,source,mapper.writeValueAsString(payload==null?Map.of():payload),correlationId);
        } catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid event payload",e); }
        queueDeliveries(id, typeId, org);
        return id;
    }

    public List<Map<String,Object>> recent(UUID org, UUID userId, int limit) {
        requireMember(org,userId); int n=Math.max(1,Math.min(limit,200));
        return jdbc.queryForList("select e.id,e.event_type_id,e.source_service,e.event_time,e.payload,e.correlation_id,e.version from public.events e where e.organization_id=? order by e.event_time desc limit ?",org,n);
    }

    public UUID subscribe(UUID userId, SubscriptionRequest r) {
        requireMember(r.organizationId(),userId);
        if (!Set.of("service","user").contains(r.subscriberType())) throw new IllegalArgumentException("Invalid subscriber type");
        if ("service".equals(r.subscriberType()) && (r.endpointUrl()==null || !r.endpointUrl().startsWith("https://"))) throw new IllegalArgumentException("Service endpoints must use HTTPS");
        UUID id=UUID.randomUUID();
        try {
            jdbc.update("insert into public.event_subscriptions(id,organization_id,subscriber_type,subscriber_id,event_type_id,filter,endpoint_url) values (?,?,?,?,?,?,?)",
                    id,r.organizationId(),r.subscriberType(),r.subscriberId(),r.eventTypeId(),r.filter()==null?null:mapper.writeValueAsString(r.filter()),r.endpointUrl());
        } catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid subscription filter",e); }
        return id;
    }

    private void queueDeliveries(UUID eventId, UUID typeId, UUID org) {
        jdbc.update("insert into public.event_deliveries(event_id,subscription_id) select ?,id from public.event_subscriptions where organization_id=? and event_type_id=? and active=true on conflict(event_id,subscription_id) do nothing",eventId,org,typeId);
    }

    public void deliverPending(int batchSize) {
        List<Map<String,Object>> rows=jdbc.queryForList("select d.id,d.event_id,d.subscription_id,e.payload,e.source_service,e.event_time,e.correlation_id,s.endpoint_url from public.event_deliveries d join public.events e on e.id=d.event_id join public.event_subscriptions s on s.id=d.subscription_id where d.status='pending' order by d.id limit ?",Math.max(1,Math.min(batchSize,500)));
        for (Map<String,Object> r:rows) deliver(r);
    }

    private void deliver(Map<String,Object> r) {
        long delivery=((Number)r.get("id")).longValue();
        String endpoint=(String)r.get("endpoint_url");
        try {
            if(endpoint!=null){
                String body=mapper.writeValueAsString(Map.of("id",r.get("event_id"),"sourceService",r.get("source_service"),"eventTime",r.get("event_time"),"payload",r.get("payload"),"correlationId",r.get("correlation_id")));
                HttpRequest req=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<Void> resp=http.send(req,HttpResponse.BodyHandlers.discarding());
                if(resp.statusCode()<200||resp.statusCode()>=300) throw new IllegalStateException("Subscriber HTTP "+resp.statusCode());
            }
            jdbc.update("update public.event_deliveries set status='delivered',attempts=attempts+1,last_attempt_at=now(),delivered_at=now(),error_message=null where id=?",delivery);
        } catch(Exception e){
            jdbc.update("update public.event_deliveries set status=case when attempts+1>=10 then 'dead_letter' else 'failed' end,attempts=attempts+1,last_attempt_at=now(),error_message=? where id=?",String.valueOf(e.getMessage()).substring(0,Math.min(1000,String.valueOf(e.getMessage()).length())),delivery);
        }
    }

    private void requireMember(UUID org,UUID user){Integer n=jdbc.queryForObject("select count(*) from public.organization_members where organization_id=? and user_id=?",Integer.class,org,user);if(n==null||n<1)throw new SecurityException("Organization membership required");}
    public record SubscriptionRequest(UUID organizationId,String subscriberType,String subscriberId,UUID eventTypeId,Map<String,Object> filter,String endpointUrl){}
}
