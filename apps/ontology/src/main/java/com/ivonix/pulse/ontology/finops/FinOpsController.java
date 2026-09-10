package com.ivonix.pulse.ontology.finops;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/finops")
public class FinOpsController {
  private final FinOpsService service; public FinOpsController(FinOpsService service){this.service=service;}
  @PostMapping("/cost") public Map<String,String> cost(Authentication a,@RequestBody CostRequest r){service.attribute(org(a),r.service(),r.resourceType(),r.quantity(),r.unit(),r.tags());return Map.of("status","attributed");}
  @GetMapping("/profitability") public List<Map<String,Object>> profitability(Authentication a){return service.profitability(org(a));}
  @GetMapping("/costs") public List<Map<String,Object>> costs(Authentication a){return service.costs(org(a));}
  @GetMapping("/anomalies") public List<Map<String,Object>> anomalies(Authentication a){return service.anomalies(org(a));}
  private UUID org(Authentication a){Object d=a.getDetails();if(d instanceof UUID u)return u;return UUID.fromString(String.valueOf(d));}
  public record CostRequest(String service,String resourceType,double quantity,String unit,Map<String,Object> tags){}
}
