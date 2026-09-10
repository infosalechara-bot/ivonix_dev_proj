package com.ivonix.pulse.ontology.privacy;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/privacy")
public class PrivacyController {
  private final PrivacyService service; public PrivacyController(PrivacyService service){this.service=service;}
  @GetMapping("/consents") public List<Map<String,Object>> consents(Authentication a){return service.consents(user(a));}
  @PostMapping("/consent") public ResponseEntity<?> consent(Authentication a,@RequestBody ConsentRequest r){service.recordConsent(user(a),r.purpose(),r.granted(),r.version(),r.ipAddress(),r.userAgent());return ResponseEntity.noContent().build();}
  @GetMapping("/dsar") public List<Map<String,Object>> dsars(Authentication a){return service.dsars(user(a));}
  @PostMapping("/dsar") public Map<String,Object> submit(Authentication a,@RequestBody Map<String,String> b){UUID id=service.submitDsar(user(a),b.get("type"));return Map.of("id",id);}
  @PostMapping("/dsar/{id}/export") public Map<String,Object> export(Authentication a,@PathVariable UUID id){return service.generateAccessExport(user(a),id);}
  @PostMapping("/dsar/{id}/erase") public Map<String,Object> erase(Authentication a,@PathVariable UUID id,@RequestBody Map<String,String> b){UUID job=service.executeErasure(user(a),id,UUID.fromString(b.get("confirmationId")));return Map.of("jobId",job);}
  @PostMapping("/dp/count") public Map<String,Double> dp(Authentication a,@RequestBody Map<String,String> b){return Map.of("count",service.privateCount(user(a),b.get("metric"),Double.parseDouble(b.getOrDefault("epsilon","1"))));}
  private UUID user(Authentication a){return UUID.fromString(a.getName());}
  public record ConsentRequest(String purpose,boolean granted,String version,String ipAddress,String userAgent){}
}
