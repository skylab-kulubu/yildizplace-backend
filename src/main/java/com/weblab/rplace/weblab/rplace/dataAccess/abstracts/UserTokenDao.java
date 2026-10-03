package com.weblab.rplace.weblab.rplace.dataAccess.abstracts;

import com.weblab.rplace.weblab.rplace.entities.UserToken;
import com.weblab.rplace.weblab.rplace.entities.UserTokenKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.List;

// Rows are found by the SHA-256 of their value (TokenHashes), never by the value.
@Repository
public interface UserTokenDao extends JpaRepository<UserToken, Integer>{

    UserToken findByTokenHash(String tokenHash);

    // Rows without a kind are sessions from before login links were told apart.
    @Query("SELECT t FROM UserToken t WHERE t.tokenHash = :tokenHash AND (t.kind IS NULL OR t.kind = com.weblab.rplace.weblab.rplace.entities.UserTokenKind.SESSION)")
    UserToken findSessionByTokenHash(String tokenHash);

    List<UserToken> findAllByUserIp(String userIp);

    List<UserToken> findAllByCreatedAtBetweenAndUserIpAndKind(Date startDate, Date endDate, String userIp, UserTokenKind kind);

    List<UserToken> findAllByCreatedAtBetweenAndUserIdAndKind(Date startDate, Date endDate, int userId, UserTokenKind kind);

    // Marks an unexpired login link used in one statement and returns 1 if it logs in. A single-use
    // link must also be unused, so it logs in at most once even when it is opened twice at the same
    // moment; a reusable one (reusable = true) logs in again until it expires.
    @Transactional
    @Modifying
    @Query("UPDATE UserToken t SET t.isUsed = true, t.usedAt = :usedAt WHERE t.tokenHash = :tokenHash AND t.kind = com.weblab.rplace.weblab.rplace.entities.UserTokenKind.LINK AND t.createdAt > :createdAfter AND (:reusable = true OR t.isUsed = false)")
    int useLink(String tokenHash, Date usedAt, Date createdAfter, boolean reusable);

    @Transactional
    @Modifying
    @Query("DELETE FROM UserToken t WHERE t.tokenHash = :tokenHash AND (t.kind IS NULL OR t.kind = com.weblab.rplace.weblab.rplace.entities.UserTokenKind.SESSION)")
    int deleteSession(String tokenHash);

    // Elevated sessions past their end (only they have one); nobody can use them any more.
    @Transactional
    @Modifying
    @Query("DELETE FROM UserToken t WHERE t.expiresAt <= :now")
    int deleteEndedBy(Instant now);

    // The one row an earlier version stored with this value in the clear, moved to its hash.
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "UPDATE user_tokens SET token_hash = :tokenHash, token = NULL WHERE token = :token AND token_hash IS NULL")
    int hashStoredInTheClear(String token, String tokenHash);

    // Every row an earlier version stored in the clear, moved to its hash (the same SHA-256 hex as TokenHashes).
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "UPDATE user_tokens SET token_hash = encode(sha256(convert_to(token, 'UTF8')), 'hex'), token = NULL WHERE token IS NOT NULL")
    int hashAllStoredInTheClear();

}
