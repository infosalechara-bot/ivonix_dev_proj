package com.ivonix.pulse.ontology.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class OfflineSyncService {
    private static final Set<String> ALLOWED_TABLES=Set.of("device_telemetry","find_searches","find_matches","ontology_entities","ontology_relationships");
    private final JdbcTemplate jdbc; private final ObjectMapper mapper;
    public OfflineSyncService(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}

    @Scheduled(fixedDelayString="${PULSE_EDGE_SYNC_DELAY_MS:60000}")
    @Transactional
    public void syncPending(){
        List<Map<String,Object>> rows=jdbc.queryForList("select id,organization_id,table_name,record_id,operation,data from public.sync_queue where synced=false order by created_at for update skip locked limit 100");
        for(Map<String,Object> row:rows){try{apply(row);jdbc.update("update public.sync_queue set synced=true,synced_at=now(),last_error=null where id=?",row.get("id"));}catch(Exception e){jdbc.update("update public.sync_queue set attempts=attempts+1,last_error=? where id=?",truncate(e.getMessage()),row.get("id"));}}
    }

    private void apply(Map<String,Object> row) throws Exception{
        String table=String.valueOf(row.get("table_name")); if(!ALLOWED_TABLES.contains(table)) throw new IllegalArgumentException("Table not allowed");
        String op=String.valueOf(row.get("operation")); UUID org=(UUID)row.get("organization_id"); UUID id=(UUID)row.get("record_id"); JsonNode data=mapper.readTree(String.valueOf(row.get("data")));
        if(op.equals("delete")){jdbc.update("delete from public."+table+" where id=?",id);return;}
        if(!op.equals("insert")&&!op.equals("update"))throw new IllegalArgumentException("Operation not allowed");
        String json=mapper.writeValueAsString(data);
        jdbc.update("select public.apply_sync_record(?::text, ?::uuid, ?::uuid, ?::text, ?::jsonb)",table,id,org,op,json);
    }
    private String truncate(String s){if(s==null)return "sync failed";return s.length()>1000?s.substring(0,1000):s;}
}
