package com.ivonix.pulse.ontology.runbook;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/runbook")
public class RunbookController {
  private final RunbookService service; public RunbookController(RunbookService service){this.service=service;}
  @GetMapping("/incidents") public List<Map<String,Object>> incidents(Authentication a){return service.incidents(org(a));}
  @PostMapping("/incidents") public Map<String,Object> declare(Authentication a,@RequestBody DeclareRequest r){UUID id=service.declare(org(a),user(a),r.title(),r.severity(),r.affectedServices(),r.customerImpact());return Map.of("id",id);}
  @GetMapping("/incidents/{id}/timeline") public List<Map<String,Object>> timeline(Authentication a,@PathVariable UUID id){return service.timeline(org(a),id);}
  @PostMapping("/incidents/{id}/timeline") public Map<String,String> note(Authentication a,@PathVariable UUID id,@RequestBody NoteRequest r){service.addNote(org(a),id,user(a),r.eventType(),r.content());return Map.of("status","recorded");}
  @PostMapping("/incidents/{id}/status") public Map<String,String> status(Authentication a,@PathVariable UUID id,@RequestBody StatusRequest r){service.updateStatus(org(a),id,user(a),r.status(),r.rootCause());return Map.of("status",r.status());}
  @GetMapping("/alerts") public List<Map<String,Object>> alerts(Authentication a){return service.alerts(org(a));}
  @PostMapping("/alerts/{id}/ack") public Map<String,String> ack(Authentication a,@PathVariable UUID id){service.acknowledgeAlert(org(a),id,user(a));return Map.of("status","acknowledged");}
  @PostMapping("/alerts/{id}/incident/{incidentId}") public Map<String,Object> attach(Authentication a,@PathVariable UUID id,@PathVariable UUID incidentId){return Map.of("incidentId",service.attachAlert(org(a),id,incidentId));}
  @GetMapping("/teams") public List<Map<String,Object>> teams(Authentication a){return service.teams(org(a));}
  @GetMapping("/schedules") public List<Map<String,Object>> schedules(Authentication a){return service.schedules(org(a));}
  private UUID user(Authentication a){return UUID.fromString(a.getName());} private UUID org(Authentication a){Object d=a.getDetails();if(d instanceof UUID u)return u;return UUID.fromString(String.valueOf(d));}
  public record DeclareRequest(String title,String severity,List<String> affectedServices,String customerImpact){}
  public record NoteRequest(String eventType,String content){}
  public record StatusRequest(String status,String rootCause){}
}
