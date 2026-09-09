package com.ivonix.pulse.ontology.intelligence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OntologyIntelligenceService {
    private final JdbcTemplate jdbc;
    public OntologyIntelligenceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String,Object>> graph(UUID entityId, int depth, UUID orgId) {
        int safeDepth = Math.max(0, Math.min(depth, 8));
        assertEntityMember(entityId, orgId);
        return jdbc.queryForList("select * from public.get_entity_graph(?, ?, ?)", entityId, safeDepth, orgId);
    }

    public List<Map<String,Object>> pageRank(UUID orgId, int iterations) {
        int safeIterations = Math.max(1, Math.min(iterations, 100));
        return jdbc.queryForList("select * from public.graph_pagerank(?, ?)", orgId, safeIterations);
    }

    public void relationship(UUID source, UUID target, String type, UUID orgId) {
        assertEntityMember(source, orgId); assertEntityMember(target, orgId);
        jdbc.update("insert into public.ontology_relationships(id, organization_id, relationship_type, source_entity_id, target_entity_id, properties) values (gen_random_uuid(), ?, ?, ?, ?, '{}'::jsonb)", orgId, type, source, target);
    }

    private void assertEntityMember(UUID entityId, UUID orgId) {
        Integer count = jdbc.queryForObject("select count(*) from public.ontology_entities e join public.organization_members m on m.organization_id=e.organization_id where e.id=? and e.organization_id=? and m.user_id=?", Integer.class, entityId, orgId, currentUser());
        if (count == null || count == 0) throw new org.springframework.security.access.AccessDeniedException("Entity access denied");
    }

    private UUID currentUser() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UUID id)) throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        return id;
    }
}
