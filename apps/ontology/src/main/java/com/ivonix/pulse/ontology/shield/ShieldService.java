package com.ivonix.pulse.ontology.shield;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class ShieldService {
    private final JdbcTemplate jdbc; private final RestTemplate http; private final ObjectMapper mapper; private final String vtKey;
    public ShieldService(JdbcTemplate jdbc,RestTemplate http,ObjectMapper mapper,Environment env){this.jdbc=jdbc;this.http=http;this.mapper=mapper;this.vtKey=env.getProperty("VIRUSTOTAL_API_KEY","");}

    public long ingest(UUID orgId,UUID userId,UUID deviceId,String type,String severity,Map<String,Object> details,String sourceIp){
        requireMember(orgId,userId); validateSeverity(severity); validateType(type);
        try {return jdbc.queryForObject("insert into public.security_events(organization_id,device_id,event_type,severity,details,source_ip,timestamp) values(?,?,?,?,?::jsonb,?::inet,now()) returning id",Long.class,orgId,deviceId,type,severity,mapper.writeValueAsString(details==null?Map.of():details),sourceIp);} catch(Exception e){throw new IllegalArgumentException("Invalid security event",e);}
    }

    public Map<String,Object> scanHash(UUID orgId,UUID userId,String hash){
        requireMember(orgId,userId); if(!hash.matches("^[A-Fa-f0-9]{32,128}$")) throw new IllegalArgumentException("Invalid file hash"); if(vtKey.isBlank()) throw new IllegalStateException("VirusTotal integration is not configured");
        HttpHeaders h=new HttpHeaders();h.set("x-apikey",vtKey);h.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<Map> r=http.exchange("https://www.virustotal.com/api/v3/files/"+hash,HttpMethod.GET,new HttpEntity<>(h),Map.class);
        return r.getBody()==null?Map.of():r.getBody();
    }

    private void requireMember(UUID org,UUID user){Integer c=jdbc.queryForObject("select count(*) from public.organization_members where organization_id=? and user_id=?",Integer.class,org,user);if(c==null||c==0)throw new org.springframework.security.access.AccessDeniedException("Organization access denied");}
    private void validateSeverity(String s){if(s==null||!Set.of("info","low","medium","high","critical").contains(s.toLowerCase()))throw new IllegalArgumentException("Invalid severity");}
    private void validateType(String s){if(s==null||!Set.of("intrusion","malware","anomaly","login_failure").contains(s.toLowerCase()))throw new IllegalArgumentException("Invalid event type");}
}
