package com.ivonix.pulse.ontology.digitaltwin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final JdbcTemplate jdbc;
    private final RestTemplate rest;
    private final ObjectMapper mapper;
    private final String simulationUrl;

    public DigitalTwinService(JdbcTemplate jdbc, RestTemplate rest, ObjectMapper mapper,
                              org.springframework.core.env.Environment env) {
        this.jdbc = jdbc; this.rest = rest; this.mapper = mapper;
        this.simulationUrl = env.getProperty("PULSE_SIMULATION_URL", "http://127.0.0.1:8091/simulate");
    }

    @Transactional
    public UUID createTwin(UUID orgId, UUID userId, TwinRequest r) {
        requireMember(orgId, userId);
        requireDevice(orgId, r.deviceId());
        if (!Set.of("thermal", "vibration", "energy").contains(r.simulationModel()))
            throw new IllegalArgumentException("Unsupported simulation model");
        UUID id = UUID.randomUUID();
        jdbc.update("insert into public.digital_twins(id,organization_id,device_id,name,simulation_model,parameters,created_by) values(?,?,?,?,?,?,?)",
                id, orgId, r.deviceId(), r.name(), r.simulationModel(), json(r.parameters()), userId);
        return id;
    }

    @Transactional
    public UUID runSimulation(UUID orgId, UUID userId, UUID twinId, Map<String,Object> input) {
        requireMember(orgId, userId);
        TwinRow twin = twin(twinId, orgId);
        UUID runId = UUID.randomUUID();
        jdbc.update("insert into public.simulation_runs(id,twin_id,input_data,status) values(?,?,?,'running')",
                runId, twinId, json(input == null ? Map.of() : input));
        try {
            SimulationRequest req = new SimulationRequest(twinId, runId, input == null ? Map.of() : input);
            HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
            rest.postForEntity(simulationUrl, new HttpEntity<>(req, h), String.class);
            return runId;
        } catch (RuntimeException ex) {
            jdbc.update("update public.simulation_runs set status='failed',completed_at=now(),output_data=?::jsonb where id=? and twin_id=?",
                    json(Map.of("error", "simulation worker unavailable")), runId, twinId);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> twins(UUID orgId, UUID userId, UUID deviceId) {
        requireMember(orgId, userId); requireDevice(orgId, deviceId);
        return jdbc.queryForList("select id,name,device_id,simulation_model,parameters,created_at,updated_at from public.digital_twins where organization_id=? and device_id=? order by created_at desc", orgId, deviceId);
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> predictions(UUID orgId, UUID userId, UUID deviceId) {
        requireMember(orgId, userId); requireDevice(orgId, deviceId);
        return jdbc.queryForList("select id,twin_id,failure_type,probability,time_horizon,recommended_action,created_at from public.predicted_failures where device_id=? order by created_at desc limit 50", deviceId);
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> snapshots(UUID orgId, UUID userId, UUID twinId) {
        requireMember(orgId, userId); twin(twinId, orgId);
        return jdbc.queryForList("select id,timestamp,state from public.twin_snapshots where twin_id=? order by timestamp desc limit 100", twinId);
    }

    private TwinRow twin(UUID id, UUID orgId) {
        return jdbc.query("select id,device_id,simulation_model,parameters from public.digital_twins where id=? and organization_id=?",
                rs -> rs.next() ? new TwinRow(rs.getObject(1,UUID.class), rs.getObject(2,UUID.class), rs.getString(3), rs.getString(4)) : null, id, orgId) != null
                ? jdbc.query("select id,device_id,simulation_model,parameters from public.digital_twins where id=? and organization_id=?",
                    rs -> rs.next() ? new TwinRow(rs.getObject(1,UUID.class), rs.getObject(2,UUID.class), rs.getString(3), rs.getString(4)) : null, id, orgId)
                : throw new AccessDeniedException("Digital twin access denied");
    }

    private void requireDevice(UUID orgId, UUID deviceId) {
        Integer n = jdbc.queryForObject("select count(*) from public.devices where id=? and organization_id=?", Integer.class, deviceId, orgId);
        if (n == null || n != 1) throw new AccessDeniedException("Device access denied");
    }
    private void requireMember(UUID orgId, UUID userId) {
        Integer n = jdbc.queryForObject("select count(*) from public.organization_members where organization_id=? and user_id=?", Integer.class, orgId, userId);
        if (n == null || n != 1) throw new AccessDeniedException("Organization access denied");
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value == null ? Map.of() : value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid JSON payload", e); }
    }

    private record TwinRow(UUID id, UUID deviceId, String model, String parameters) {}
    public record TwinRequest(UUID deviceId, String name, String simulationModel, Map<String,Object> parameters) {}
    public record SimulationRequest(UUID twinId, UUID runId, Map<String,Object> inputData) {}
}
