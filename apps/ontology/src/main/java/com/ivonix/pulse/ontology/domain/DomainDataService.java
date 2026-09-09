package com.ivonix.pulse.ontology.domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.*;

public abstract class DomainDataService {
    protected final JdbcTemplate jdbc;
    private final String table;
    private final Set<String> allowedColumns;

    protected DomainDataService(JdbcTemplate jdbc, String table, Set<String> allowedColumns) {
        this.jdbc = jdbc; this.table = table; this.allowedColumns = Set.copyOf(allowedColumns);
    }

    public void ingest(UUID deviceId, UUID orgId, Map<String,Object> data) {
        requireDevice(deviceId, orgId);
        if (data == null || data.isEmpty()) throw new IllegalArgumentException("No telemetry fields supplied");
        List<String> columns = data.keySet().stream().filter(allowedColumns::contains).toList();
        if (columns.size() != data.size()) throw new IllegalArgumentException("Unsupported domain field");
        String sql = "insert into public." + table + " (device_id," + String.join(",", columns) + ") values (?" + ",?".repeat(columns.size()) + ")";
        List<Object> args = new ArrayList<>(); args.add(deviceId); columns.forEach(c -> args.add(data.get(c)));
        jdbc.update(sql, args.toArray());
    }

    public List<Map<String,Object>> latest(UUID deviceId, UUID orgId, int limit) {
        requireDevice(deviceId, orgId); int n=Math.max(1,Math.min(limit,100));
        return jdbc.queryForList("select * from public."+table+" where device_id=? order by timestamp desc limit "+n, deviceId);
    }

    protected void requireDevice(UUID deviceId, UUID orgId) {
        Integer n=jdbc.queryForObject("select count(*) from public.devices where id=? and organization_id=?",Integer.class,deviceId,orgId);
        if(n==null || n!=1) throw new AccessDeniedException("Device access denied");
    }

    protected double number(Map<String,Object> row,String key){ Object v=row.get(key); return v instanceof Number ? ((Number)v).doubleValue() : Double.NaN; }
}