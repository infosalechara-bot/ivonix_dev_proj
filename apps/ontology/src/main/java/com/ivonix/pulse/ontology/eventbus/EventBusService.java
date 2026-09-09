package com.ivonix.pulse.ontology.eventbus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
public class EventBusService {
    private static final int MAX_ATTEMPTS = 10;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

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
        if (r.endpointUrl() != null) validateEndpoint(r.endpointUrl());
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
        int n=Math.max(1,Math.min(batchSize,500));
        List<Map<String,Object>> rows=jdbc.queryForList(
                "select d.id,d.event_id,d.subscription_id,d.attempts,d.last_attempt_at,e.payload,e.source_service,e.event_time,e.correlation_id,s.endpoint_url " +
                "from public.event_deliveries d join public.events e on e.id=d.event_id join public.event_subscriptions s on s.id=d.subscription_id " +
                "where (d.status='pending' or (d.status='failed' and d.attempts < ? and (d.last_attempt_at is null or d.last_attempt_at < now() - make_interval(secs => least(300, greatest(5, power(2, least(d.attempts, 8)))))))) " +
                "order by coalesce(d.last_attempt_at,to_timestamp(0)),d.id limit ?",
                MAX_ATTEMPTS,n);
        for (Map<String,Object> r:rows) deliver(r);
    }

    private void deliver(Map<String,Object> r) {
        long delivery=((Number)r.get("id")).longValue();
        String endpoint=(String)r.get("endpoint_url");
        try {
            if(endpoint!=null){
                validateEndpoint(endpoint);
                String body=mapper.writeValueAsString(Map.of("id",r.get("event_id"),"sourceService",r.get("source_service"),"eventTime",r.get("event_time"),"payload",r.get("payload"),"correlationId",r.get("correlation_id")));
                HttpRequest req=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<Void> resp=http.send(req,HttpResponse.BodyHandlers.discarding());
                if(resp.statusCode()<200||resp.statusCode()>=300) throw new IllegalStateException("Subscriber HTTP "+resp.statusCode());
            }
            jdbc.update("update public.event_deliveries set status='delivered',attempts=attempts+1,last_attempt_at=now(),delivered_at=now(),error_message=null where id=? and status in ('pending','failed')",delivery);
        } catch(Exception e){
            String message=String.valueOf(e.getMessage());
            jdbc.update("update public.event_deliveries set status=case when attempts+1>=? then 'dead_letter' else 'failed' end,attempts=attempts+1,last_attempt_at=now(),error_message=? where id=? and status in ('pending','failed')",MAX_ATTEMPTS,message.substring(0,Math.min(1000,message.length())),delivery);
        }
    }

    private void validateEndpoint(String endpoint) {
        URI uri;
        try { uri=URI.create(endpoint); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid webhook URL",e); }
        if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo()!=null || uri.getHost()==null)
            throw new IllegalArgumentException("Webhook URL must be HTTPS with a public hostname");
        try {
            for(InetAddress address: InetAddress.getAllByName(uri.getHost())) {
                if(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || isDocumentationOrSpecial(address))
                    throw new IllegalArgumentException("Webhook destination is not publicly routable");
            }
        } catch (java.net.UnknownHostException e) { throw new IllegalArgumentException("Webhook hostname could not be resolved",e); }
    }

    private boolean isDocumentationOrSpecial(InetAddress address) {
        byte[] b=address.getAddress();
        if(b.length==4){int a=b[0]&255,c=b[1]&255; return a==0 || a==100&&c>=64&&c<=127 || a==198&&(b[2]&255)>=18&&(b[2]&255)<=19 || a==192&&c==0 || a==192&&c==0&& (b[2]&255)==2;}
        return false;
    }

    private void requireMember(UUID org,UUID user){Integer n=jdbc.queryForObject("select count(*) from public.organization_members where organization_id=? and user_id=?",Integer.class,org,user);if(n==null||n<1)throw new SecurityException("Organization membership required");}
    public record SubscriptionRequest(UUID organizationId,String subscriberType,String subscriberId,UUID eventTypeId,Map<String,Object> filter,String endpointUrl){}
}
