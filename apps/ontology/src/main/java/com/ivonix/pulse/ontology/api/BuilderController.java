package com.ivonix.pulse.ontology.api;

import com.ivonix.pulse.ontology.aistudio.AiStudioService;
import com.ivonix.pulse.ontology.appforge.AppForgeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class BuilderController {
 private final AiStudioService ai; private final AppForgeService forge;
 public BuilderController(AiStudioService ai,AppForgeService forge){this.ai=ai;this.forge=forge;}
 private UUID user(){return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());}
 private UUID org(){Object d=SecurityContextHolder.getContext().getAuthentication().getDetails();if(!(d instanceof UUID))throw new SecurityException("Organization context missing");return (UUID)d;}
 @PostMapping("/ai-studio/datasets") public Map<String,Object> dataset(@Valid @RequestBody DatasetRequest r){return Map.of("id",ai.createDataset(org(),user(),r.name(),r.description(),r.storagePath(),r.format()));}
 @PostMapping("/ai-studio/models") public Map<String,Object> model(@Valid @RequestBody ModelRequest r){return Map.of("id",ai.createModel(org(),user(),r.name(),r.modelType(),r.framework(),r.hyperparameters(),r.datasetId()));}
 @PostMapping("/ai-studio/models/{id}/train") public Map<String,Object> train(@PathVariable UUID id){return Map.of("jobId",ai.train(id,org(),user()));}
 @GetMapping("/ai-studio/models") public Object models(){return ai.models(org(),user());}
 @GetMapping("/ai-studio/jobs") public Object jobs(){return ai.jobs(org(),user());}
 @PostMapping("/ai-studio/models/{id}/deploy/{deviceId}") public Map<String,Object> deploy(@PathVariable UUID id,@PathVariable UUID deviceId){return Map.of("deploymentId",ai.deploy(id,deviceId,org(),user()));}
 @PostMapping("/app-forge/apps") public Map<String,Object> app(@Valid @RequestBody AppRequest r){return Map.of("id",forge.createApp(org(),user(),r.name(),r.description(),r.models()));}
 @GetMapping("/app-forge/apps/{appId}/tables/{table}") public Object list(@PathVariable UUID appId,@PathVariable String table){return forge.list(appId,org(),user(),table);}
 @PostMapping("/app-forge/apps/{appId}/tables/{table}") public ResponseEntity<Void> insert(@PathVariable UUID appId,@PathVariable String table,@RequestBody Map<String,Object> data){forge.insert(appId,org(),user(),table,data);return ResponseEntity.noContent().build();}
 public record DatasetRequest(@NotBlank @Size(max=200) String name,@Size(max=2000) String description,@Size(max=500) String storagePath,@Size(max=30) String format){}
 public record ModelRequest(@NotBlank @Size(max=200) String name,@NotBlank String modelType,@NotBlank String framework,Map<String,Object> hyperparameters,UUID datasetId){}
 public record AppRequest(@NotBlank @Size(max=100) String name,@Size(max=2000) String description,@NotEmpty @Size(max=30) List<AppForgeService.ModelDef> models){}
}
