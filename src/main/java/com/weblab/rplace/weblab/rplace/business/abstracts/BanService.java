package com.weblab.rplace.weblab.rplace.business.abstracts;

import com.weblab.rplace.weblab.rplace.core.utilities.results.DataResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.entities.BannedIp;
import com.weblab.rplace.weblab.rplace.entities.BannedUser;
import com.weblab.rplace.weblab.rplace.entities.ModerationAuditEntry;

import java.util.List;

public interface BanService {

    DataResult<List<BannedIp>> getBannedIps();

    DataResult<List<BannedUser>> getBannedUsers();

    Result banIp(String ip, String reason);

    Result banUser(String schoolMail, String reason);

    // Lifts the ban on this address; the audit log keeps who made and who lifted it.
    Result unbanIp(String ip, String reason);

    // Lifts the ban on the user with this school address; the audit log keeps who made and who lifted it.
    Result unbanUser(String schoolMail, String reason);

    DataResult<BannedIp> isIpBanned(String ip);

    DataResult<BannedUser> isUserBanned(String bannedUserSchoolMail);

    // For the checks at every login and on every request with a session.
    boolean isIpAddressBanned(String ip);

    boolean isUserIdBanned(int userId);

    // Bans, unbans and whitelist changes, newest first.
    DataResult<List<ModerationAuditEntry>> getAuditLog();

}
