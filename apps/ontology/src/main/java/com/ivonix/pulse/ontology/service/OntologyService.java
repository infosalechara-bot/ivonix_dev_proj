package com.ivonix.pulse.ontology.service;

import com.ivonix.pulse.ontology.api.CreateEntityRequest;
import com.ivonix.pulse.ontology.domain.OntologyEntity;
import com.ivonix.pulse.ontology.repository.OntologyEntityRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class OntologyService {
  private final OntologyEntityRepository entities;
  private final JdbcTemplate jdbc;
  public OntologyService(OntologyEntityRepository entities, JdbcTemplate jdbc){this.entities=entities;this.jdbc=jdbc;}

  public OntologyEntity create(CreateEntityRequest dto, UUID authenticatedOrgId){
    if(!dto.organizationId().equals(authenticatedOrgId)) throw new SecurityException("Organization mismatch");
    OntologyEntity e=new OntologyEntity(); e.setOrganizationId(dto.organizationId()); e.setTypeId(dto.typeId()); e.setDisplayName(dto.displayName()); e.setProperties(dto.properties()); e.setSourceDeviceId(dto.sourceDeviceId());
    return entities.save(e);
  }

  public List<Map<String,Object>> graph(UUID entityId,int depth,UUID orgId){
    int safeDepth=Math.max(0,Math.min(depth,8));
    return jdbc.queryForList("select * from public.get_entity_graph(?, ?, ?)",entityId,safeDepth,orgId);
  }

  public List<OntologyEntity> search(String query,UUID orgId){
    if(query==null || query.isBlank()) return List.of();
    return entities.searchEntities(orgId,query.trim());
  }
}