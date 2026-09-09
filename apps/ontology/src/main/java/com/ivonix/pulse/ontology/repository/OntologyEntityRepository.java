package com.ivonix.pulse.ontology.repository;

import com.ivonix.pulse.ontology.domain.OntologyEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface OntologyEntityRepository extends JpaRepository<OntologyEntity, UUID> {
  @Query(value="SELECT * FROM public.ontology_entities e WHERE e.organization_id=:orgId AND (e.display_name ILIKE CONCAT('%',:query,'%') OR COALESCE(e.properties->>'description','') ILIKE CONCAT('%',:query,'%')) LIMIT 100", nativeQuery=true)
  List<OntologyEntity> searchEntities(@Param("orgId") UUID orgId, @Param("query") String query);
  Optional<OntologyEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);
}