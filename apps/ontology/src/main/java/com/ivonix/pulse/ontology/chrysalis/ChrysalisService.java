package com.ivonix.pulse.ontology.chrysalis;

import com.fasterxml.jackson.databind.ObjectMapper; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.security.access.AccessDeniedException; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.util.*;

public record UpgradeRecommendation(String capabilityNeeded,String recommendedUpgrade,String complexity,Double estimatedCost,String instructions) {}
@Service public class ChrysalisService {
 private final JdbcTemplate jdbc; private final ObjectMapper mapper;
 public ChrysalisService(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 @Transactional(readOnly=true) public List<UpgradeRecommendation> assess(UUID deviceId,UUID orgId,List<String> capabilities){requireDevice(deviceId,orgId); if(capabilities==null||capabilities.isEmpty()) return List.of(); return jdbc.query("select cm.capability_needed,cm.recommended_upgrade,cm.complexity,cm.estimated_cost,cm.instructions from public.compatibility_matrix cm join public.devices d on d.id=? where cm.capability_needed = any(?) and (cm.legacy_model_pattern ilike '%'||coalesce(d.type,'')||'%' or cm.legacy_model_pattern ilike '%'||coalesce(d.name,'')||'%')",(rs,n)->new UpgradeRecommendation(rs.getString(1),rs.getString(2),rs.getString(3),rs.getObject(4,Double.class),rs.getString(5)),deviceId,capabilities.toArray(new String[0])); }
 @Transactional public void execute(UUID upgradePathId,UUID deviceId,UUID orgId){requireDevice(deviceId,orgId); Integer ok=jdbc.queryForObject("select count(*) from public.upgrade_paths where id=? and device_id=? and organization_id=?",Integer.class,upgradePathId,deviceId,orgId); if(ok==null||ok!=1) throw new AccessDeniedException("Upgrade path access denied"); jdbc.update("insert into public.installed_upgrades(upgrade_path_id,device_id,organization_id,status) values(?,?,?,'active')",upgradePathId,deviceId,orgId); jdbc.update("update public.upgrade_paths set status='installed' where id=?",upgradePathId); }
 private void requireDevice(UUID d,UUID o){Integer n=jdbc.queryForObject("select count(*) from public.devices where id=? and organization_id=?",Integer.class,d,o);if(n==null||n!=1)throw new AccessDeniedException("Device access denied");}
}