package com.weblab.rplace.weblab.rplace.dataAccess.abstracts;

import com.weblab.rplace.weblab.rplace.entities.BannedUser;
import com.weblab.rplace.weblab.rplace.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BannedUserDao extends JpaRepository<BannedUser, Integer> {
    BannedUser findByBannedBy(User bannedBy);

    BannedUser findByBannedUser(User bannedUser);

}
