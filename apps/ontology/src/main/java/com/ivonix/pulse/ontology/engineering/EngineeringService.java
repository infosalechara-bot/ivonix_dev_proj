package com.ivonix.pulse.ontology.engineering;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class EngineeringService {
    private final JdbcTemplate jdbc; private final RestTemplate http; private final ObjectMapper mapper; private final String aiUrl;
    public EngineeringService(JdbcTemplate jdbc, RestTemplate http, ObjectMapper mapper, Environment env){this.jdbc=jdbc;this.http=http;this.mapper=mapper;this.aiUrl=env.getProperty("PULSE_AI_URL","http://ai-service:8000");}

    @Transactional
    public Map<String,Object> diagnose(UUID deviceId, UUID orgId, UUID userId, List<UUID> evidenceIds, Map<String,Object> telemetry){
        requireDeviceAccess(deviceId,orgId,userId);
        List<String> types = evidenceIds == null || evidenceIds.isEmpty() ? List.of() : jdbc.queryForList("select evidence_type from public.inspection_evidence where device_id=? and organization_id=? and id = any(?::uuid[])",String.class,deviceId,orgId,evidenceIds.toArray(new UUID[0]));
        String domain=jdbc.queryForObject("select domain from public.machine_dna where device_id=?",String.class,deviceId);
        Map<String,Object> req=Map.of("telemetry",telemetry==null?Map.of():telemetry,"evidenceTypes",types,"deviceDomain",domain==null?"unknown":domain);
        Map response=http.postForObject(aiUrl+"/diagnose",req,Map.class);
        if(response==null) throw new IllegalStateException("AI diagnostic service returned no result");
        UUID id=UUID.randomUUID();
        jdbc.update("insert into public.diagnostic_results(id,device_id,organization_id,probable_fault,confidence,evidence,recommended_actions,do_not_disassemble,created_by) values(?,?,?,?,?,?,?::jsonb,?,?)",
                id,deviceId,orgId,String.valueOf(response.getOrDefault("probableFault","Unknown")),((Number)response.getOrDefault("confidence",0)).doubleValue(),mapper.writeValueAsString(Map.of("types",types)),mapper.writeValueAsString(response.getOrDefault("recommendedActions",List.of())),Boolean.TRUE.equals(response.get("doNotDisassemble")),userId);
        Map<String,Object> result=new LinkedHashMap<>(response); result.put("id",id); return result;
    }

    private void requireDeviceAccess(UUID deviceId,UUID orgId,UUID userId){Integer c=jdbc.queryForObject("select count(*) from public.devices d join public.organization_members m on m.organization_id=d.organization_id where d.id=? and d.organization_id=? and m.user_id=?",Integer.class,deviceId,orgId,userId);if(c==null||c==0)throw new org.springframework.security.access.AccessDeniedException("Device access denied");}
}
