package com.weblab.rplace.weblab.rplace.dataAccess.abstracts;

import com.weblab.rplace.weblab.rplace.entities.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface UserTokenDao extends JpaRepository<UserToken, Integer>{

    UserToken findByToken(String token);

    List<UserToken> findAllByUserIp(String userIp);

    List<UserToken> findAllByCreatedAtBetweenAndUserIp(Date startDate, Date endDate, String userIp);

    List<UserToken> findAllByCreatedAtBetweenAndUserId(Date startDate, Date endDate, int userId);

}
