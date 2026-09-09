package com.ivonix.pulse.ontology.find;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FindService {
    private final JdbcTemplate jdbc; private final RestTemplate http; private final ObjectMapper mapper; private final String embeddingUrl;
    public FindService(JdbcTemplate jdbc,RestTemplate http,ObjectMapper mapper,org.springframework.core.env.Environment env){this.jdbc=jdbc;this.http=http;this.mapper=mapper;this.embeddingUrl=env.getProperty("PULSE_EMBEDDING_URL","http://embedding-service:8000");}
    @Transactional
    public UUID register(UUID serviceId,UUID orgId,UUID userId,String entityType,Map<String,Object> entityData,Instant consentAt,String consentVersion){
        requireMember(userId,orgId); if(consentAt==null||consentVersion==null||consentVersion.isBlank())throw new IllegalArgumentException("Explicit consent and consent version are required");
        UUID id=UUID.randomUUID();
        jdbc.update("insert into public.find_registrations(id,service_id,organization_id,entity_type,entity_data,status,created_by,consent_at,consent_version) values(?,?,?,?,?::jsonb,'active',?,?,?)",id,serviceId,orgId,entityType,writeJson(entityData),userId,consentAt,consentVersion);
        return id;
    }
    @Transactional public UUID createSearch(UUID serviceId,UUID orgId,UUID userId,Map<String,Object> query){requireMember(userId,orgId);UUID id=UUID.randomUUID();jdbc.update("insert into public.find_searches(id,service_id,organization_id,query_data,created_by) values(?,?,?,?,?)",id,serviceId,orgId,writeJson(query),userId);return id;}
    @Transactional(readOnly=true) public List<Map<String,Object>> search(UUID searchId,UUID orgId,UUID userId,String embedding,String embeddingType,int limit){requireMember(userId,orgId);int safeLimit=Math.max(1,Math.min(limit,50));String sql="select r.id registration_id,r.entity_data,e.embedding_type match_type,1-(e.embedding <=> ?::vector) confidence from public.find_embeddings e join public.find_registrations r on r.id=e.registration_id join public.find_searches s on s.id=? and s.organization_id=? where r.organization_id=? and r.status='active' and e.embedding_type=? order by e.embedding <=> ?::vector limit "+safeLimit;return jdbc.queryForList(sql,embedding,searchId,orgId,orgId,embeddingType,embedding);}
    public String generateEmbedding(String type,String source,String text){return http.postForObject(embeddingUrl+"/generate",Map.of("type",type,"source",source==null?"":source,"text",text==null?"":text),String.class);}
    private void requireMember(UUID userId,UUID orgId){Integer c=jdbc.queryForObject("select count(*) from public.organization_members where user_id=? and organization_id=?",Integer.class,userId,orgId);if(c==null||c==0)throw new org.springframework.security.access.AccessDeniedException("Organization access denied");}
    private String writeJson(Object v){try{return mapper.writeValueAsString(v);}catch(Exception e){throw new IllegalArgumentException("Invalid JSON payload",e);}}
}
