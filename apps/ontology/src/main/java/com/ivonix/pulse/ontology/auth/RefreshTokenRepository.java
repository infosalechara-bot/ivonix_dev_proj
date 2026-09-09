package com.ivonix.pulse.ontology.auth;

import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import jakarta.persistence.LockModeType; import java.util.*;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken,UUID>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from RefreshToken t where t.tokenHash=:hash") Optional<RefreshToken> findForUpdate(@Param("hash") String hash);
 @Modifying @Query("delete from RefreshToken t where t.expiresAt <= CURRENT_TIMESTAMP or t.revokedAt is not null") int deleteExpiredOrRevoked();
}
