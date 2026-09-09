package com.ivonix.pulse.ontology.auth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RefreshTokenCleanupJob {
  private final RefreshTokenRepository tokens;
  public RefreshTokenCleanupJob(RefreshTokenRepository tokens){this.tokens=tokens;}
  @Scheduled(cron="0 20 3 * * *",zone="UTC")
  @Transactional
  public void cleanup(){tokens.deleteExpiredOrRevoked();}
}
