package com.weblab.rplace.weblab.rplace.dataAccess.abstracts;

import com.weblab.rplace.weblab.rplace.entities.UserToken;
import com.weblab.rplace.weblab.rplace.entities.UserTokenKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Repository
public interface UserTokenDao extends JpaRepository<UserToken, Integer>{

    UserToken findByToken(String token);

    // Rows without a kind are sessions from before login links were told apart.
    @Query("SELECT t FROM UserToken t WHERE t.token = :token AND (t.kind IS NULL OR t.kind = com.weblab.rplace.weblab.rplace.entities.UserTokenKind.SESSION)")
    UserToken findSessionByToken(String token);

    List<UserToken> findAllByUserIp(String userIp);

    List<UserToken> findAllByCreatedAtBetweenAndUserIpAndKind(Date startDate, Date endDate, String userIp, UserTokenKind kind);

    List<UserToken> findAllByCreatedAtBetweenAndUserIdAndKind(Date startDate, Date endDate, int userId, UserTokenKind kind);

    // Marks an unused, unexpired login link used in one statement, so a link logs in at most
    // once even when it is opened twice at the same moment. Returns 1 if this call used it.
    @Transactional
    @Modifying
    @Query("UPDATE UserToken t SET t.isUsed = true, t.usedAt = :usedAt WHERE t.token = :token AND t.kind = com.weblab.rplace.weblab.rplace.entities.UserTokenKind.LINK AND t.isUsed = false AND t.createdAt > :createdAfter")
    int markLinkUsed(String token, Date usedAt, Date createdAfter);

}
