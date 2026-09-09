package com.ivonix.pulse.ontology.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="ontology_relationships")
public class OntologyRelationship {
  @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
  @Column(name="organization_id", nullable=false) private UUID organizationId;
  @Column(name="relationship_type", nullable=false, length=120) private String relationshipType;
  @Column(name="source_entity_id", nullable=false) private UUID sourceEntityId;
  @Column(name="target_entity_id", nullable=false) private UUID targetEntityId;
  @Column(columnDefinition="jsonb") private String properties;
  public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public String getRelationshipType(){return relationshipType;} public UUID getSourceEntityId(){return sourceEntityId;} public UUID getTargetEntityId(){return targetEntityId;} public String getProperties(){return properties;}
  public void setOrganizationId(UUID v){organizationId=v;} public void setRelationshipType(String v){relationshipType=v;} public void setSourceEntityId(UUID v){sourceEntityId=v;} public void setTargetEntityId(UUID v){targetEntityId=v;} public void setProperties(String v){properties=v;}
}