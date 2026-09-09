package com.ivonix.pulse.ontology.edge;

import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Service; import java.util.*;
@Service public class OfflineSyncService {
 private final OfflineDatabase local; private final JdbcTemplate cloud;
 public OfflineSyncService(OfflineDatabase local,JdbcTemplate cloud){this.local=local;this.cloud=cloud;}
 @Scheduled(fixedDelayString="${PULSE_EDGE_SYNC_DELAY_MS:60000}") public void syncPending(){if(!online())return;try{for(var row:local.pending(50)){long id=(Long)row.get("id");try{cloud.queryForObject("select public.apply_sync_record(?::text,?::uuid,?::uuid,?::text,?::jsonb)",(rs,n)->rs.getObject(1),row.get("table_name"),UUID.fromString((String)row.get("record_id")),UUID.fromString((String)row.get("organization_id")),row.get("operation"),row.get("data"));local.markSynced(id);}catch(Exception e){local.markFailed(id,e.getMessage());}}}catch(Exception ignored){}}
 private boolean online(){try{cloud.queryForObject("select 1",Integer.class);return true;}catch(Exception e){return false;}}
}