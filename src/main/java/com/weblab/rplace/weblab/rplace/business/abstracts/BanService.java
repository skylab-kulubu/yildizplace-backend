package com.weblab.rplace.weblab.rplace.business.abstracts;

import com.weblab.rplace.weblab.rplace.core.utilities.results.DataResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.entities.BannedIp;
import com.weblab.rplace.weblab.rplace.entities.BannedUser;
import com.weblab.rplace.weblab.rplace.entities.User;

import javax.xml.crypto.Data;
import java.util.List;

public interface BanService {

    DataResult<List<BannedIp>> getBannedIps();

    DataResult<List<BannedUser>> getBannedUsers();

    Result banIp(String ip, String reason);

    Result banUser(String schoolMail, String reason);

    Result unbanIp(int ip);

    Result unbanUser(int userId);

    DataResult<BannedIp> isIpBanned(String ip);

    DataResult<BannedUser> isUserBanned(String bannedUserSchoolMail);

}
