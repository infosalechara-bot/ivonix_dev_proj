package com.ivonix.pulse.ontology.domain;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.regex.Pattern;

public abstract class DomainService {
    private static final Pattern COLUMN = Pattern.compile("[a-z][a-z0-9_]*");
    protected final JdbcTemplate jdbc;
    private final String table;
    private final Set<String> allowedColumns;

    protected DomainService(JdbcTemplate jdbc, String table, Set<String> allowedColumns) {
        this.jdbc = jdbc;
        this.table = table;
        this.allowedColumns = Set.copyOf(allowedColumns);
    }

    public void ingest(UUID deviceId, Map<String, Object> data) {
        if (deviceId == null || data == null || data.isEmpty()) throw new IllegalArgumentException("deviceId and data are required");
        var columns = new ArrayList<String>();
        var values = new ArrayList<Object>();
        for (var entry : data.entrySet()) {
            if (!COLUMN.matcher(entry.getKey()).matches() || !allowedColumns.contains(entry.getKey()))
                throw new IllegalArgumentException("Unsupported domain field: " + entry.getKey());
            columns.add(entry.getKey()); values.add(entry.getValue());
        }
        String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
        jdbc.update("INSERT INTO public." + table + " (device_id, " + String.join(", ", columns) + ") VALUES (?, " + placeholders + ")", prepend(deviceId, values));
    }

    public List<Map<String,Object>> latest(UUID deviceId, int limit) {
        int safe = Math.max(1, Math.min(limit, 100));
        return jdbc.queryForList("SELECT * FROM public." + table + " WHERE device_id=? ORDER BY timestamp DESC LIMIT ?", deviceId, safe);
    }

    protected boolean ownsDevice(UUID deviceId, UUID orgId) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM public.devices WHERE id=? AND organization_id=? )", Boolean.class, deviceId, orgId);
    }

    private Object[] prepend(UUID id, List<Object> values) { var out = new Object[values.size()+1]; out[0]=id; for(int i=0;i<values.size();i++) out[i+1]=values.get(i); return out; }
}
