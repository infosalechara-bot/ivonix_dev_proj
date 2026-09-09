package com.ivonix.pulse.ontology.api;

import com.ivonix.pulse.ontology.founder.FounderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/founder")
public class FounderController {
  private final FounderService founder;
  public FounderController(FounderService founder){this.founder=founder;}

  @GetMapping("/profile")
  public ResponseEntity<?> profile(Authentication auth){
    UUID user=UUID.fromString(auth.getName()); UUID org=org(auth);
    var p=founder.profile(user,org);
    return p==null?ResponseEntity.status(403).body(Map.of("error","Founder authorization required")):ResponseEntity.ok(p);
  }

  @GetMapping("/messages")
  public List<FounderService.Message> messages(Authentication auth){return founder.messages(UUID.fromString(auth.getName()),org(auth));}

  @PostMapping("/messages")
  public FounderService.Message message(Authentication auth,@RequestBody FounderService.MessageRequest request){return founder.sendMessage(UUID.fromString(auth.getName()),org(auth),request);}

  @PostMapping("/confirmations")
  public FounderService.ConfirmationRequest requestConfirmation(Authentication auth,@RequestBody Map<String,String> body){
    return founder.requestConfirmation(UUID.fromString(auth.getName()),org(auth),body.get("action"));
  }

  @PostMapping("/confirmations/{id}/decision")
  public FounderService.ConfirmationStatus decision(Authentication auth,@PathVariable UUID id,@RequestBody DecisionRequest request){
    return founder.confirm(UUID.fromString(auth.getName()),org(auth),id,request.token(),request.approve());
  }

  @PostMapping("/confirmations/expire")
  public Map<String,Integer> expire(Authentication auth){return Map.of("expired",founder.expirePending(UUID.fromString(auth.getName()),org(auth)));}

  private UUID org(Authentication auth){Object details=auth.getDetails();if(details instanceof UUID u)return u;return UUID.fromString(String.valueOf(details));}
  public record DecisionRequest(String token,boolean approve){}
}