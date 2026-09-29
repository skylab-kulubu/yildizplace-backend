package com.weblab.rplace.weblab.rplace.dataAccess.abstracts;

import com.weblab.rplace.weblab.rplace.entities.EskylabLoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
public interface EskylabLoginAttemptDao extends JpaRepository<EskylabLoginAttempt, String> {

    // Returns 1 if this call removed the attempt: of two callbacks with the same state only one gets 1.
    @Transactional
    @Modifying
    @Query("DELETE FROM EskylabLoginAttempt a WHERE a.state = :state")
    int deleteByState(String state);

    @Transactional
    @Modifying
    @Query("DELETE FROM EskylabLoginAttempt a WHERE a.createdAt < :cutoff")
    int deleteCreatedBefore(Instant cutoff);
}
