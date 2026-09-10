package com.ivonix.pulse.ontology.digitaltwin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class DigitalTwinService {
    private static final String SIMULATION_SCHEMA_VERSION = "1.0";
    private final JdbcTemplate jdbc; private final RestTemplate rest; private final ObjectMapper mapper; private final String simulationUrl; private final String simulationSecret;
    public DigitalTwinService(JdbcTemplate jdbc, RestTemplate rest, ObjectMapper mapper, org.springframework.core.env.Environment env) {
        this.jdbc=jdbc; this.rest=rest; this.mapper=mapper;
        this.simulationUrl=env.getProperty("PULSE_SIMULATION_URL", "http://digital-twin:8091/simulate");
        this.simulationSecret=env.getProperty("PULSE_TWIN_SERVICE_SECRET", "");
    }
    @Transactional
    public UUID createTwin(UUID orgId, UUID userId, TwinRequest r) {
        requireMember(orgId,userId); requireDevice(orgId,r.deviceId());
        if(!Set.of("thermal","vibration","energy").contains(r.simulationModel())) throw new IllegalArgumentException("Unsupported simulation model");
        UUID id=UUID.randomUUID();
        jdbc.update("insert into public.digital_twins(id,organization_id,device_id,name,simulation_model,parameters,created_by) values(?,?,?,?,?,?,?)",id,orgId,r.deviceId(),r.name(),r.simulationModel(),json(r.parameters()),userId);
        return id;
    }
    public UUID runSimulation(UUID orgId, UUID userId, UUID twinId, Map<String,Object> input) {
        requireMember(orgId,userId); UUID deviceId=twinDevice(twinId,orgId);
        if(simulationSecret.isBlank()) throw new IllegalStateException("PULSE_TWIN_SERVICE_SECRET is required");
        Map<String,Object> safeInput=input==null?new LinkedHashMap<>():new LinkedHashMap<>(input); latestTelemetry(deviceId,orgId).forEach(safeInput::putIfAbsent);
        UUID runId=UUID.randomUUID(); jdbc.update("insert into public.simulation_runs(id,twin_id,input_data,status) values(?,?,?,'running')",runId,twinId,json(safeInput));
        try {
            HttpHeaders h=new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); h.setBearerAuth(simulationSecret);
            SimulationRequest request=new SimulationRequest(SIMULATION_SCHEMA_VERSION,orgId,twinId,runId,safeInput,UUID.randomUUID().toString());
            rest.postForEntity(simulationUrl,new HttpEntity<>(request,h),String.class); return runId;
        } catch(RuntimeException ex){ jdbc.update("update public.simulation_runs set status='failed',completed_at=now(),output_data=?::jsonb where id=? and twin_id=?",json(Map.of("error","simulation worker unavailable")),runId,twinId); throw ex; }
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> twins(UUID orgId,UUID userId,UUID deviceId){ requireMember(orgId,userId);requireDevice(orgId,deviceId);return jdbc.queryForList("select id,name,device_id,simulation_model,parameters,created_at,updated_at from public.digital_twins where organization_id=? and device_id=? order by created_at desc",orgId,deviceId); }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> predictions(UUID orgId,UUID userId,UUID deviceId){ requireMember(orgId,userId);requireDevice(orgId,deviceId);return jdbc.queryForList("select id,twin_id,failure_type,probability,time_horizon,recommended_action,created_at from public.predicted_failures where device_id=? and organization_id=? order by created_at desc limit 50",deviceId,orgId); }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> snapshots(UUID orgId,UUID userId,UUID twinId){ requireMember(orgId,userId);twinDevice(twinId,orgId);return jdbc.queryForList("select id,timestamp,state from public.twin_snapshots where twin_id=? order by timestamp desc limit 100",twinId); }
    private UUID twinDevice(UUID id,UUID orgId){ try{return jdbc.queryForObject("select device_id from public.digital_twins where id=? and organization_id=?",UUID.class,id,orgId);}catch(EmptyResultDataAccessException e){throw new AccessDeniedException("Digital twin access denied");} }
    private Map<String,Object> latestTelemetry(UUID deviceId,UUID orgId){ List<Map<String,Object>> rows=jdbc.queryForList("select data from public.device_telemetry where device_id=? and organization_id=? order by timestamp desc limit 1",deviceId,orgId); if(rows.isEmpty()||rows.get(0).get("data")==null)return Map.of(); Object data=rows.get(0).get("data"); if(data instanceof Map<?,?> m){Map<String,Object> out=new LinkedHashMap<>();m.forEach((k,v)->out.put(String.valueOf(k),v));return out;} try{return mapper.readValue(String.valueOf(data),Map.class);}catch(Exception e){return Map.of();} }
    private void requireDevice(UUID orgId,UUID deviceId){Integer n=jdbc.queryForObject("select count(*) from public.devices where id=? and organization_id=?",Integer.class,deviceId,orgId);if(n==null||n!=1)throw new AccessDeniedException("Device access denied");}
    private void requireMember(UUID orgId,UUID userId){Integer n=jdbc.queryForObject("select count(*) from public.organization_members where organization_id=? and user_id=?",Integer.class,orgId,userId);if(n==null||n!=1)throw new AccessDeniedException("Organization access denied");}
    private String json(Object value){try{return mapper.writeValueAsString(value==null?Map.of():value);}catch(JsonProcessingException e){throw new IllegalArgumentException("Invalid JSON payload",e);}}
    public record TwinRequest(UUID deviceId,String name,String simulationModel,Map<String,Object> parameters){}
    public record SimulationRequest(String schemaVersion,UUID organizationId,UUID twinId,UUID runId,Map<String,Object> inputData,String correlationId){}
}
