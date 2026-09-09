package com.ivonix.pulse.ontology.repository;

import com.ivonix.pulse.ontology.domain.OntologyRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface OntologyRelationshipRepository extends JpaRepository<OntologyRelationship, UUID> {
  List<OntologyRelationship> findByOrganizationIdAndSourceEntityIdOrOrganizationIdAndTargetEntityId(UUID org1, UUID source, UUID org2, UUID target);
}