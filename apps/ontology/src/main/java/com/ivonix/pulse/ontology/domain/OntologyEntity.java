package com.ivonix.pulse.ontology.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="ontology_entities")
public class OntologyEntity {
  @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
  @Column(name="organization_id", nullable=false) private UUID organizationId;
  @Column(name="type_id", nullable=false) private UUID typeId;
  @Column(name="display_name", nullable=false, length=255) private String displayName;
  @Column(columnDefinition="jsonb") private String properties;
  @Column(name="source_device_id") private UUID sourceDeviceId;
  public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getTypeId(){return typeId;} public String getDisplayName(){return displayName;} public String getProperties(){return properties;} public UUID getSourceDeviceId(){return sourceDeviceId;}
  public void setOrganizationId(UUID v){organizationId=v;} public void setTypeId(UUID v){typeId=v;} public void setDisplayName(String v){displayName=v;} public void setProperties(String v){properties=v;} public void setSourceDeviceId(UUID v){sourceDeviceId=v;}
}