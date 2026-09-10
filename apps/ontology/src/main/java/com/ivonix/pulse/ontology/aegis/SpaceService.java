package com.ivonix.pulse.ontology.aegis;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class SpaceService {
    private final JdbcTemplate jdbc;
    public SpaceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public UUID ingestSatellite(UUID orgId, UUID userId, SatelliteRequest r) {
        requireMember(orgId, userId);
        if (r.noradId() == null || r.noradId().isBlank()) throw new IllegalArgumentException("NORAD ID is required");
        UUID id = UUID.randomUUID();
        jdbc.update("insert into public.satellites(id,organization_id,name,norad_id,tle_line1,tle_line2,status) values(?,?,?,?,?,?,coalesce(?, 'active'))", id, orgId, r.name(), r.noradId(), r.tleLine1(), r.tleLine2(), r.status());
        return id;
    }

    @Transactional(readOnly = true)
    public List<SatelliteView> satellites(UUID orgId, UUID userId) {
        requireMember(orgId, userId);
        return jdbc.query("select id,organization_id,name,norad_id,tle_line1,tle_line2,status from public.satellites where organization_id=? order by name nulls last", (rs,n) -> new SatelliteView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7)), orgId);
    }

    @Transactional(readOnly = true)
    public List<CollisionView> predictions(UUID orgId, UUID userId, UUID satelliteId) {
        requireMember(orgId, userId); requireSatellite(satelliteId, orgId);
        return jdbc.query("select id,satellite_id,object_name,object_norad_id,time_of_closest_approach,miss_distance_meters,probability,severity from public.collision_predictions where organization_id=? and satellite_id=? order by time_of_closest_approach nulls last", (rs,n) -> new CollisionView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getObject(5,OffsetDateTime.class),rs.getObject(6,Double.class),rs.getObject(7,Double.class),rs.getString(8)), orgId, satelliteId);
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> comms(UUID orgId, UUID userId, UUID satelliteId) {
        requireMember(orgId, userId); requireSatellite(satelliteId, orgId);
        return jdbc.queryForList("select id,timestamp,uplink_status,downlink_status,signal_strength,interference_detected from public.satellite_comms where organization_id=? and satellite_id=? order by timestamp desc limit 100", orgId, satelliteId);
    }

    private void requireMember(UUID orgId, UUID userId) { Integer n=jdbc.queryForObject("select count(*) from public.organization_members where organization_id=? and user_id=?",Integer.class,orgId,userId); if(n==null||n<1) throw new AccessDeniedException("Organization access denied"); }
    private void requireSatellite(UUID id, UUID orgId) { Integer n=jdbc.queryForObject("select count(*) from public.satellites where id=? and organization_id=?",Integer.class,id,orgId); if(n==null||n!=1) throw new AccessDeniedException("Satellite access denied"); }

    public record SatelliteView(UUID id, UUID organizationId, String name, String noradId, String tleLine1, String tleLine2, String status) {}
    public record CollisionView(UUID id, UUID satelliteId, String objectName, String objectNoradId, OffsetDateTime tca, Double missDistanceMeters, Double probability, String severity) {}
    public record SatelliteRequest(String name,String noradId,String tleLine1,String tleLine2,String status) {}
}
