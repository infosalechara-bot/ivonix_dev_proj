package com.ivonix.pulse.ontology.intelligence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class InvestigativeService {
    private final JdbcTemplate jdbc;

    public InvestigativeService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public UUID createCase(UUID orgId, UUID userId, CaseRequest r) {
        requireMember(orgId, userId);
        requireText(r.title(), "title", 200);
        if (r.description() != null && r.description().length() > 10000) throw new IllegalArgumentException("Description too long");
        if (r.caseNumber() != null && r.caseNumber().length() > 100) throw new IllegalArgumentException("Case number too long");
        String priority = r.priority() == null ? "medium" : r.priority();
        if (!Set.of("low", "medium", "high", "critical").contains(priority)) throw new IllegalArgumentException("Invalid priority");
        if (r.assignedTo() != null) requireMember(orgId, r.assignedTo());
        UUID id = UUID.randomUUID();
        jdbc.update("insert into investigation_cases(id,organization_id,case_number,title,description,status,priority,created_by,assigned_to) values (?,?,?,?,?,'open',?,?,?)",
                id, orgId, r.caseNumber(), r.title(), r.description(), priority, userId, r.assignedTo());
        audit(userId, "case.create", "investigation_case", id, orgId);
        return id;
    }

    public List<Map<String,Object>> listCases(UUID orgId, UUID userId) {
        requireMember(orgId, userId);
        return jdbc.queryForList("select id,case_number,title,description,status,priority,created_by,assigned_to,created_at,updated_at from investigation_cases where organization_id=? order by updated_at desc", orgId);
    }

    public void addEvidence(UUID orgId, UUID userId, UUID caseId, EvidenceRequest r) {
        requireMember(orgId, userId); requireCase(orgId, caseId); requireEntity(orgId, r.entityId());
        requireText(r.evidenceType(), "evidenceType", 50);
        if (r.filePath() != null && r.filePath().length() > 2000) throw new IllegalArgumentException("filePath too long");
        jdbc.update("insert into case_evidence(case_id,entity_id,evidence_type,file_path,notes,added_by) values (?,?,?,?,?,?)", caseId,r.entityId(),r.evidenceType(),r.filePath(),r.notes(),userId);
        audit(userId,"evidence.add","case_evidence",caseId,orgId);
    }

    public void addTimeline(UUID orgId, UUID userId, UUID caseId, TimelineRequest r) {
        requireMember(orgId, userId); requireCase(orgId, caseId);
        if (r.eventTime() == null) throw new IllegalArgumentException("eventTime is required");
        if (r.entityId() != null) requireEntity(orgId, r.entityId());
        jdbc.update("insert into case_timeline_events(case_id,event_time,title,description,entity_id,created_by) values (?,?,?,?,?,?)",caseId,r.eventTime(),r.title(),r.description(),r.entityId(),userId);
        audit(userId,"timeline.add","case_timeline_event",caseId,orgId);
    }

    public void addLink(UUID orgId, UUID userId, UUID caseId, LinkRequest r) {
        requireMember(orgId, userId); requireCase(orgId, caseId); requireEntity(orgId, r.sourceEntityId()); requireEntity(orgId, r.targetEntityId());
        requireText(r.linkType(), "linkType", 100);
        double weight = r.weight() == null ? 1d : r.weight();
        if (!Double.isFinite(weight) || weight < 0) throw new IllegalArgumentException("Invalid link weight");
        jdbc.update("insert into case_links(case_id,source_entity_id,target_entity_id,link_type,weight,notes,created_by) values (?,?,?,?,?,?,?)",caseId,r.sourceEntityId(),r.targetEntityId(),r.linkType(),weight,r.notes(),userId);
        audit(userId,"link.create","case_link",caseId,orgId);
    }

    public List<Map<String,Object>> getEvidence(UUID orgId, UUID userId, UUID caseId) {
        requireMember(orgId,userId); requireCase(orgId,caseId);
        return jdbc.queryForList("select ce.*,e.display_name from case_evidence ce join ontology_entities e on e.id=ce.entity_id where ce.case_id=? order by ce.added_at desc",caseId);
    }

    public List<Map<String,Object>> getTimeline(UUID orgId, UUID userId, UUID caseId) {
        requireMember(orgId,userId); requireCase(orgId,caseId);
        return jdbc.queryForList("select * from case_timeline_events where case_id=? order by event_time asc",caseId);
    }

    public List<Map<String,Object>> getLinks(UUID orgId, UUID userId, UUID caseId) {
        requireMember(orgId,userId); requireCase(orgId,caseId);
        return jdbc.queryForList("select cl.*,s.display_name source_name,t.display_name target_name from case_links cl join ontology_entities s on s.id=cl.source_entity_id join ontology_entities t on t.id=cl.target_entity_id where cl.case_id=? order by cl.weight desc",caseId);
    }

    public List<Map<String,Object>> commonNeighbors(UUID orgId, UUID userId, UUID a, UUID b) {
        requireMember(orgId,userId); requireEntity(orgId,a); requireEntity(orgId,b);
        return jdbc.queryForList("select e.* from ontology_entities e where e.organization_id=? and e.id in (select r1.target_entity_id from ontology_relationships r1 where r1.source_entity_id=? and r1.organization_id=? intersect select r2.target_entity_id from ontology_relationships r2 where r2.source_entity_id=? and r2.organization_id=?)",orgId,a,orgId,b,orgId);
    }

    public int commonNeighborCount(UUID orgId, UUID userId, UUID a, UUID b) {
        requireMember(orgId,userId); requireEntity(orgId,a); requireEntity(orgId,b);
        return jdbc.queryForObject("select public.common_neighbor_count(?,?,?)",Integer.class,a,b,orgId);
    }

    public List<Map<String,Object>> shortestPath(UUID orgId, UUID userId, UUID start, UUID end) {
        requireMember(orgId,userId); requireEntity(orgId,start); requireEntity(orgId,end);
        return jdbc.queryForList("select * from public.graph_shortest_path(?,?,?)",start,end,orgId);
    }

    private void requireCase(UUID orgId, UUID caseId) {
        Integer n=jdbc.queryForObject("select count(*) from investigation_cases where id=? and organization_id=?",Integer.class,caseId,orgId);
        if(n==null||n!=1) throw new SecurityException("Case not found in organization");
    }
    private void requireEntity(UUID orgId, UUID id) {
        if(id==null) throw new IllegalArgumentException("Entity is required");
        Integer n=jdbc.queryForObject("select count(*) from ontology_entities where id=? and organization_id=?",Integer.class,id,orgId);
        if(n==null||n!=1) throw new SecurityException("Entity not found in organization");
    }
    private void requireMember(UUID orgId, UUID userId) {
        Integer n=jdbc.queryForObject("select count(*) from organization_members where organization_id=? and user_id=?",Integer.class,orgId,userId);
        if(n==null||n!=1) throw new SecurityException("Organization membership required");
    }
    private static void requireText(String value,String field,int max){if(value==null||value.isBlank()||value.length()>max)throw new IllegalArgumentException("Invalid "+field);}
    private void audit(UUID userId,String action,String resourceType,UUID resourceId,UUID orgId){
        try { jdbc.update("insert into audit_logs(user_id,action,resource_type,resource_id) values (?,?,?,?)",userId,action,resourceType,resourceId.toString()); } catch (RuntimeException ignored) { }
    }

    public record CaseRequest(String caseNumber,String title,String description,String priority,UUID assignedTo) {}
    public record EvidenceRequest(UUID entityId,String evidenceType,String filePath,String notes) {}
    public record TimelineRequest(OffsetDateTime eventTime,String title,String description,UUID entityId) {}
    public record LinkRequest(UUID sourceEntityId,UUID targetEntityId,String linkType,Double weight,String notes) {}
}
