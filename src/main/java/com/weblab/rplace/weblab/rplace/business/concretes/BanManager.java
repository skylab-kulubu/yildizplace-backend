package com.weblab.rplace.weblab.rplace.business.concretes;

import com.weblab.rplace.weblab.rplace.business.abstracts.BanService;
import com.weblab.rplace.weblab.rplace.business.abstracts.UserService;
import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.core.utilities.results.*;
import com.weblab.rplace.weblab.rplace.dataAccess.abstracts.BannedIpDao;
import com.weblab.rplace.weblab.rplace.dataAccess.abstracts.BannedUserDao;
import com.weblab.rplace.weblab.rplace.entities.BannedIp;
import com.weblab.rplace.weblab.rplace.entities.BannedUser;
import com.weblab.rplace.weblab.rplace.entities.ModerationAuditEntry;
import com.weblab.rplace.weblab.rplace.entities.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class BanManager implements BanService {

    private final BannedIpDao bannedIpDao;

    private final BannedUserDao bannedUserDao;

    private final UserService userService;

    private final ModerationAudit audit;

    public BanManager(BannedIpDao bannedIpDao, BannedUserDao bannedUserDao, UserService userService, ModerationAudit audit) {
        this.bannedIpDao = bannedIpDao;
        this.bannedUserDao = bannedUserDao;
        this.userService = userService;
        this.audit = audit;
    }

    @Override
    public DataResult<List<BannedIp>> getBannedIps() {
        var result = bannedIpDao.findAll();

        if (result == null){
            return new ErrorDataResult<>(Messages.bannedIpsNotFound);
        }

        return new SuccessDataResult<>(result, Messages.bannedIpsFound);
    }

    @Override
    public DataResult<List<BannedUser>> getBannedUsers() {
        var result = bannedUserDao.findAll();

        if (result == null){
            return new ErrorDataResult<>(Messages.bannedUsersNotFound);
        }

        return new SuccessDataResult<>(result, Messages.bannedUsersFound);
    }

    @Override
    public Result banIp(String ip, String reason) {
        var loggedInUserResult = userService.getAuthenticatedUser();

        if (!loggedInUserResult.isSuccess()){
            return loggedInUserResult;
        }

        var loggedInUser = loggedInUserResult.getData();

        if(CheckIfIpAlreadyBanned(ip)){
            return new ErrorResult(Messages.ipAlreadyBanned);
        }


        BannedIp bannedIp = BannedIp.builder()
                .bannedBy(loggedInUser)
                .bannedAt(new Date())
                .ip(ip)
                .reason(reason)
                .build();

        bannedIpDao.save(bannedIp);
        audit.record(ModerationAuditEntry.BAN_IP, ip, reason);

        return new SuccessResult(Messages.ipBanSuccess);
    }

    private boolean CheckIfIpAlreadyBanned(String ip) {

        var result = isIpBanned(ip);

        if (result.isSuccess()){
            return true;
        }
        return false;
    }

    @Override
    public Result banUser(String schoolMail, String reason) {
       var loggedInUserResult = userService.getAuthenticatedUser();

       if (!loggedInUserResult.isSuccess()){
           return loggedInUserResult;
         }

        var loggedInUser = loggedInUserResult.getData();
        schoolMail = User.normalizeSchoolMail(schoolMail);

        // The ban would end the very session that could lift it.
        if (schoolMail.equals(loggedInUser.getSchoolMail())) {
            return new ErrorResult(Messages.cannotBanYourself);
        }

        if(CheckIfUserAlreadyBanned(schoolMail)){
            return new ErrorResult(Messages.userAlreadyBanned);
        }


        DataResult<User> userToBanResult = userService.getUserBySchoolMail(schoolMail);

        if (!userToBanResult.isSuccess()){
            return userToBanResult;
        }

        BannedUser bannedUser = BannedUser.builder()
                .bannedUser(userToBanResult.getData())
                .bannedAt(new Date())
                .reason(reason)
                .bannedBy(loggedInUser)
                .build();

        bannedUserDao.save(bannedUser);
        audit.record(ModerationAuditEntry.BAN_USER, schoolMail, reason);
        return new SuccessResult(Messages.userBanSuccess);
    }

    private boolean CheckIfUserAlreadyBanned(String schoolMail) {

        var result = isUserBanned(schoolMail);

        if (result.isSuccess()){
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public Result unbanIp(String ip, String reason) {
        BannedIp bannedIp = bannedIpDao.findByIp(ip);
        if (bannedIp == null) {
            return new ErrorResult(Messages.ipNotBanned);
        }

        bannedIpDao.delete(bannedIp);
        audit.record(ModerationAuditEntry.UNBAN_IP, ip, reason);
        return new SuccessResult(Messages.ipUnbanSuccess);
    }

    @Override
    @Transactional
    public Result unbanUser(String schoolMail, String reason) {
        schoolMail = User.normalizeSchoolMail(schoolMail);
        DataResult<User> userResult = userService.getUserBySchoolMail(schoolMail);
        if (!userResult.isSuccess()) {
            return new ErrorResult(userResult.getMessage());
        }

        BannedUser bannedUser = bannedUserDao.findByBannedUser(userResult.getData());
        if (bannedUser == null) {
            return new ErrorResult(Messages.userNotBanned);
        }

        bannedUserDao.delete(bannedUser);
        audit.record(ModerationAuditEntry.UNBAN_USER, schoolMail, reason);
        return new SuccessResult(Messages.userUnbanSuccess);
    }

    @Override
    public boolean isIpAddressBanned(String ip) {
        return ip != null && bannedIpDao.existsByIp(ip);
    }

    @Override
    public boolean isUserIdBanned(int userId) {
        return bannedUserDao.existsByBannedUser_Id(userId);
    }

    @Override
    public DataResult<List<ModerationAuditEntry>> getAuditLog() {
        return new SuccessDataResult<>(audit.newestFirst(), Messages.auditLogListed);
    }

    @Override
    public DataResult<BannedIp> isIpBanned(String ip) {
        var result = bannedIpDao.findByIp(ip);

        if (result == null){
            return new ErrorDataResult<>(Messages.ipNotBanned);
        }

        return new SuccessDataResult<BannedIp>(result, Messages.ipBanned);
    }

    @Override
    public DataResult<BannedUser> isUserBanned(String bannedUserSchoolMail) {
        var bannedUserResult = userService.getUserBySchoolMail(bannedUserSchoolMail);

        if (!bannedUserResult.isSuccess()){
            return new ErrorDataResult<>(bannedUserResult.getMessage());
        }

        var result = bannedUserDao.findByBannedUser(bannedUserResult.getData());

        if (result == null){
            return new ErrorDataResult<BannedUser>(Messages.userDoesNotExist);
        }

        return new SuccessDataResult<BannedUser>(result, Messages.userIsBanned);
    }
}
