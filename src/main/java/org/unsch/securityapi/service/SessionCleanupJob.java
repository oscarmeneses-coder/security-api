package org.unsch.securityapi.service;

import org.unsch.securityapi.repository.UserSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class SessionCleanupJob {
    private final UserSessionRepository sessions;

    public SessionCleanupJob(UserSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredSessions() {
        sessions.deleteByExpiresAtBefore(Instant.now());
    }
}
