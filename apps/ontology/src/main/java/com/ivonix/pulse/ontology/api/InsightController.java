package com.ivonix.pulse.ontology.api;

import com.ivonix.pulse.ontology.insight.InsightService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/insight")
public class InsightController {
 private final InsightService insight;
 public InsightController(InsightService insight){this.insight=insight;}
 private UUID user(){return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());}
 private UUID org(){Object d=SecurityContextHolder.getContext().getAuthentication().getDetails();if(!(d instanceof UUID))throw new SecurityException("Organization context missing");return (UUID)d;}
 @PostMapping("/reports") public Map<String,Object> create(@RequestBody InsightService.ReportRequest r){return Map.of("id",insight.createReport(org(),user(),r));}
 @GetMapping("/reports") public Object reports(){return insight.listReports(org(),user());}
 @PostMapping("/reports/{reportId}/execute") public Object execute(@PathVariable UUID reportId){return insight.execute(reportId,org(),user());}
 @GetMapping("/reports/{reportId}/results") public Object results(@PathVariable UUID reportId){return insight.results(reportId,org(),user());}
}
