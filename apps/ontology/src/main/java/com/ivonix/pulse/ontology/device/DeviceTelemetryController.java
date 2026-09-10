package com.ivonix.pulse.ontology.device;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceTelemetryController {
    private final JdbcTemplate jdbc;
    public DeviceTelemetryController(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @GetMapping("/{deviceId}/live")
    public Map<String,Object> live(Authentication authentication,@PathVariable UUID deviceId){
        UUID userId=UUID.fromString(authentication.getName());
        Map<String,Object> d=jdbc.queryForMap("select d.id,d.organization_id,d.device_id,d.name,d.type,d.status,d.last_seen from public.devices d where d.id=? and exists(select 1 from public.organization_members m where m.organization_id=d.organization_id and m.user_id=?)",deviceId,userId);
        Map<String,Object> t=jdbc.query("select id,timestamp,data from public.device_telemetry where device_id=? order by timestamp desc nulls last,id desc limit 1",rs->rs.next()?Map.of("id",rs.getLong("id"),"timestamp",rs.getObject("timestamp"),"data",rs.getObject("data")):Map.of(),deviceId);
        return Map.of("device",d,"latestTelemetry",t);
    }
}
