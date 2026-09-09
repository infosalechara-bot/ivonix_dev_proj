package com.ivonix.pulse.ontology.founder;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Shared enforcement point for critical platform actions.
 * A caller must present a founder-approved confirmation; consumption is atomic
 * so the same approval cannot authorize two executions.
 */
@Component
public class FounderActionGate {
  private final JdbcTemplate jdbc;
  public FounderActionGate(JdbcTemplate jdbc){this.jdbc=jdbc;}

  @Transactional
  public void requireApprovedAndConsume(UUID founderUserId, UUID organizationId, UUID confirmationId){
    Integer changed=jdbc.update("update public.executive_confirmations set consumed_at=now(), consumed_by=? where id=? and organization_id=? and requested_by=? and status='approved' and consumed_at is null and expires_at>now()",
        founderUserId,confirmationId,organizationId,founderUserId);
    if(changed!=1) throw new SecurityException("Critical action requires a valid, unconsumed founder approval");
  }
}
