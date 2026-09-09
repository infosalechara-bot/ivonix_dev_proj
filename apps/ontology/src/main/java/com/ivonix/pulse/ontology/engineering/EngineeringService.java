package com.ivonix.pulse.ontology.engineering;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class EngineeringService {
    private static final String DIAGNOSTIC_SCHEMA_VERSION = "1.0";
    private final JdbcTemplate jdbc; private final RestTemplate http; private final ObjectMapper mapper; private final String aiUrl; private final String aiSecret;
    public EngineeringService(JdbcTemplate jdbc,RestTemplate http,ObjectMapper mapper,Environment env){this.jdbc=jdbc;this.http=http;this.mapper=mapper;this.aiUrl=env.getProperty("PULSE_AI_URL","http://ai:8000");this.aiSecret=env.getProperty("PULSE_AI_SERVICE_SECRET","");}
    @Transactional public Map<String,Object> diagnose(UUID deviceId,UUID orgId,UUID userId,List<UUID> evidenceIds,Map<String,Object> telemetry){
        requireDeviceAccess(deviceId,orgId,userId);
        List<String> types=evidenceIds==null||evidenceIds.isEmpty()?List.of():jdbc.queryForList("select evidence_type from public.inspection_evidence where device_id=? and organization_id=? and id = any(?::uuid[])",String.class,deviceId,orgId,evidenceIds.toArray(new UUID[0]));
        String domain=jdbc.queryForObject("select md.domain from public.machine_dna md join public.devices d on d.id=md.device_id where md.device_id=? and d.organization_id=?",String.class,deviceId,orgId);
        Map<String,Object> req=new LinkedHashMap<>();req.put("schemaVersion",DIAGNOSTIC_SCHEMA_VERSION);req.put("telemetry",telemetry==null?Map.of():telemetry);req.put("evidence",types.stream().map(t->Map.<String,Object>of("type",t)).toList());req.put("deviceDomain",domain==null?"unknown":domain);
        if(aiSecret==null||aiSecret.isBlank())throw new IllegalStateException("PULSE_AI_SERVICE_SECRET is required");
        HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.setBearerAuth(aiSecret);
        Map<String,Object> response=http.exchange(aiUrl+"/diagnose",HttpMethod.POST,new HttpEntity<>(req,headers),Map.class).getBody();if(response==null)throw new IllegalStateException("AI diagnostic service returned no result");
        String probableFault=stringValue(response,"probableFault","probable_fault","Unknown");double confidence=numberValue(response,"confidence",0.0);Object recommendedActions=firstPresent(response,"recommendedActions","recommended_actions");if(!(recommendedActions instanceof List<?>))recommendedActions=List.of();boolean doNotDisassemble=booleanValue(response,"doNotDisassemble","do_not_disassemble",true);if(confidence<0||confidence>1)throw new IllegalStateException("AI diagnostic confidence outside [0,1]");
        UUID id=UUID.randomUUID();jdbc.update("insert into public.diagnostic_results(id,device_id,organization_id,probable_fault,confidence,evidence,recommended_actions,do_not_disassemble,created_by) values(?,?,?,?,?,?,?::jsonb,?,?)",id,deviceId,orgId,probableFault,confidence,json(Map.of("types",types)),json(recommendedActions),doNotDisassemble,userId);
        Map<String,Object> result=new LinkedHashMap<>(response);result.put("schemaVersion",DIAGNOSTIC_SCHEMA_VERSION);result.put("probableFault",probableFault);result.put("confidence",confidence);result.put("recommendedActions",recommendedActions);result.put("doNotDisassemble",doNotDisassemble);result.put("id",id);return result;
    }
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalArgumentException("Diagnostic response serialization failed",e);}}
    private Object firstPresent(Map<String,Object> response,String... keys){for(String key:keys)if(response.containsKey(key))return response.get(key);return null;}
    private String stringValue(Map<String,Object> response,String camel,String snake,String fallback){Object value=firstPresent(response,camel,snake);return value==null?fallback:String.valueOf(value);}
    private double numberValue(Map<String,Object> response,String key,double fallback){Object value=response.get(key);return value instanceof Number n?n.doubleValue():fallback;}
    private boolean booleanValue(Map<String,Object> response,String camel,String snake,boolean fallback){Object value=firstPresent(response,camel,snake);return value instanceof Boolean b?b:fallback;}
    private void requireDeviceAccess(UUID deviceId,UUID orgId,UUID userId){Integer c=jdbc.queryForObject("select count(*) from public.devices d join public.organization_members m on m.organization_id=d.organization_id where d.id=? and d.organization_id=? and m.user_id=?",Integer.class,deviceId,orgId,userId);if(c==null||c==0)throw new org.springframework.security.access.AccessDeniedException("Device access denied");}
}
