package com.ivonix.pulse.ontology.trust;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.*;

@RestController
@RequestMapping("/api/v1/trust")
public class TrustController {
  private final TrustService service; public TrustController(TrustService service){this.service=service;}
  @PostMapping("/signals") public Map<String,String> signal(Authentication a,@RequestBody SignalRequest r){service.recordSignal(user(a),r.deviceId(),r.type(),r.score(),r.details()==null?Map.of():r.details());return Map.of("status","recorded");}
  @GetMapping("/profiles/high-risk") public List<Map<String,Object>> highRisk(){return service.highRisk();}
  @GetMapping("/reports/open") public List<Map<String,Object>> reports(){return service.openReports();}
  @PostMapping("/reports") public Map<String,Object> report(Authentication a,@RequestBody ReportRequest r){return Map.of("id",service.reportContent(user(a),r.targetType(),r.targetId(),r.reason()));}
  private UUID user(Authentication a){return UUID.fromString(a.getName());}
  public record SignalRequest(UUID deviceId,String type,double score,Map<String,Object> details){}
  public record ReportRequest(String targetType,String targetId,String reason){}
}
