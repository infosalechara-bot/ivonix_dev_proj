package com.ivonix.pulse.ontology.global;
import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/i18n") public class GlobalController{private final GlobalService s;public GlobalController(GlobalService x){s=x;}private UUID u(Authentication a){return UUID.fromString(a.getName());}
@GetMapping("/{locale}/{namespace}") public Map<String,String> get(@PathVariable String locale,@PathVariable String namespace){return s.translations(locale,namespace);}
@GetMapping("/{locale}/direction") public Object direction(@PathVariable String locale){return s.direction(locale);}
@PostMapping("/format") public Map<String,String> format(@RequestBody FormatRequest r){return Map.of("value",s.format(r.locale(),r.template(),r.variables()));}
@PostMapping("/translations") @PreAuthorize("hasRole('admin')") public Map<String,Boolean> upsert(@RequestBody TranslationRequest r,Authentication a){s.upsert(r.locale(),r.namespace(),r.key(),r.value(),u(a));return Map.of("ok",true);}
@PostMapping("/preferences") public Map<String,Boolean> pref(@RequestBody PreferenceRequest r,Authentication a){s.pref(u(a),r.locale(),r.timezone());return Map.of("ok",true);}
public record FormatRequest(String locale,String template,Map<String,Object> variables){} public record TranslationRequest(String locale,String namespace,String key,String value){} public record PreferenceRequest(String locale,String timezone){}
}