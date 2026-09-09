package com.ivonix.pulse.ontology.aistudio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class AiStudioService {
    private final JdbcTemplate db;
    private final RestTemplate http;
    private final ObjectMapper json;
    private final Environment env;

    public AiStudioService(JdbcTemplate db, RestTemplate http, ObjectMapper json, Environment env) {
        this.db = db; this.http = http; this.json = json; this.env = env;
    }

    public UUID createDataset(UUID org, UUID user, String name, String description, String storagePath, String format) {
        requireMember(org, user);
        UUID id = UUID.randomUUID();
        db.update("insert into public.ai_datasets(id,organization_id,name,description,storage_path,format,created_by) values(?,?,?,?,?,?,?)",
                id, org, requireText(name, "name"), description, storagePath, format, user);
        return id;
    }

    public UUID createModel(UUID org, UUID user, String name, String type, String framework, Map<String,Object> hyperparameters, UUID datasetId) {
        requireMember(org, user);
        if (datasetId != null && !Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from public.ai_datasets where id=? and organization_id=?)", Boolean.class, datasetId, org))) throw new IllegalArgumentException("Dataset is not in the organization");
        UUID id = UUID.randomUUID();
        try {
            db.update("insert into public.ai_models(id,organization_id,name,model_type,framework,hyperparameters,dataset_id,created_by) values(?,?,?,?,?,?::jsonb,?,?)",
                    id, org, requireText(name,"name"), type, framework, json.writeValueAsString(hyperparameters == null ? Map.of() : hyperparameters), datasetId, user);
        } catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid hyperparameters", e); }
        return id;
    }

    public UUID train(UUID modelId, UUID org, UUID user) {
        requireMember(org, user);
        UUID modelOrg = db.queryForObject("select organization_id from public.ai_models where id=?", UUID.class, modelId);
        if (!org.equals(modelOrg)) throw new SecurityException("Model is not in the organization");
        UUID job = UUID.randomUUID();
        db.update("insert into public.ai_training_jobs(id,model_id,organization_id,status,created_by) values(?,?,?,'queued',?)", job, modelId, org, user);
        String url = env.getProperty("PULSE_TRAINING_URL", "http://127.0.0.1:8090");
        try { http.postForObject(url + "/train", Map.of("model_id", modelId.toString(), "job_id", job.toString()), String.class); }
        catch (Exception ignored) { db.update("update public.ai_training_jobs set status='failed',completed_at=now(),metrics=?::jsonb where id=?", "{\"error\":\"training service unavailable\"}", job); }
        return job;
    }

    public UUID deploy(UUID modelId, UUID deviceId, UUID org, UUID user) {
        requireMember(org, user);
        Boolean ok = db.queryForObject("select exists(select 1 from public.ai_models m join public.devices d on d.organization_id=m.organization_id where m.id=? and d.id=? and m.organization_id=?)", Boolean.class, modelId, deviceId, org);
        if (!Boolean.TRUE.equals(ok)) throw new SecurityException("Model/device organization mismatch");
        UUID id=UUID.randomUUID();
        db.update("insert into public.ai_deployments(id,model_id,device_id,organization_id,status) values(?,?,?,?,'active')",id,modelId,deviceId,org);
        return id;
    }

    public List<Map<String,Object>> jobs(UUID org, UUID user) { requireMember(org,user); return db.queryForList("select * from public.ai_training_jobs where organization_id=? order by created_at desc limit 100",org); }
    public List<Map<String,Object>> models(UUID org, UUID user) { requireMember(org,user); return db.queryForList("select * from public.ai_models where organization_id=? order by created_at desc limit 100",org); }

    private void requireMember(UUID org, UUID user) { if (!Boolean.TRUE.equals(db.queryForObject("select public.is_org_member(?,?)", Boolean.class, user, org))) throw new SecurityException("Organization access denied"); }
    private String requireText(String v,String n){ if(v==null||v.isBlank()||v.length()>200) throw new IllegalArgumentException(n+" is required and must be <= 200 characters"); return v.trim(); }
}
