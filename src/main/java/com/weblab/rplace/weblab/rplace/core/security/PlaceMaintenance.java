package com.weblab.rplace.weblab.rplace.core.security;

import com.weblab.rplace.weblab.rplace.dataAccess.abstracts.EskylabLoginAttemptDao;
import com.weblab.rplace.weblab.rplace.dataAccess.abstracts.UserTokenDao;
import com.weblab.rplace.weblab.rplace.entities.EskylabLoginAttempt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Removes what has ended but nobody will come back for (ticket 08): elevated
 * sessions past their end (a request with one deletes it, but most are never used
 * again) and e-skylab login attempts nobody returned from. It also hashes any login
 * value still stored in the clear (UserTokenHashing). Every 15 minutes while
 * PLACE_MAINTENANCE_ENABLED is true (the default; MaintenanceScheduling).
 *
 * <p>Ordinary sessions have no end on the server and stay; used and expired login
 * links stay too, since the hourly link limits count them.
 */
@Component
public class PlaceMaintenance {

    private static final Logger log = LoggerFactory.getLogger(PlaceMaintenance.class);

    private final UserTokenDao userTokenDao;

    private final EskylabLoginAttemptDao loginAttemptDao;

    private final UserTokenHashing userTokenHashing;

    private final Clock clock;

    public PlaceMaintenance(UserTokenDao userTokenDao, EskylabLoginAttemptDao loginAttemptDao, UserTokenHashing userTokenHashing, Clock clock) {
        this.userTokenDao = userTokenDao;
        this.loginAttemptDao = loginAttemptDao;
        this.userTokenHashing = userTokenHashing;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 1, fixedDelay = 15, timeUnit = TimeUnit.MINUTES)
    public void run() {
        Instant now = clock.instant();
        int endedSessions = userTokenDao.deleteEndedBy(now);
        int abandonedLogins = loginAttemptDao.deleteCreatedBefore(now.minus(EskylabLoginAttempt.LIFETIME));
        int hashed = userTokenHashing.hashValuesStoredInTheClear();
        if (endedSessions + abandonedLogins + hashed > 0) {
            log.info("maintenance: removed {} ended elevated sessions and {} abandoned e-skylab logins, hashed {} values",
                    endedSessions, abandonedLogins, hashed);
        }
    }
}
