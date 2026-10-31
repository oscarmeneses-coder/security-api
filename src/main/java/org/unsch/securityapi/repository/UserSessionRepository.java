package org.unsch.securityapi.repository;

import org.unsch.securityapi.entity.UserSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    Optional<UserSession> findByRefreshTokenHash(String hash);
    long deleteByExpiresAtBefore(Instant instant);
}
