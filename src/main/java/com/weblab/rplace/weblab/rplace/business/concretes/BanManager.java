package com.weblab.rplace.weblab.rplace.business.concretes;

import com.weblab.rplace.weblab.rplace.business.abstracts.BanService;
import com.weblab.rplace.weblab.rplace.business.abstracts.UserService;
import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.core.utilities.results.*;
import com.weblab.rplace.weblab.rplace.dataAccess.abstracts.BannedIpDao;
import com.weblab.rplace.weblab.rplace.dataAccess.abstracts.BannedUserDao;
import com.weblab.rplace.weblab.rplace.entities.BannedIp;
import com.weblab.rplace.weblab.rplace.entities.BannedUser;
import com.weblab.rplace.weblab.rplace.entities.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class BanManager implements BanService {

    @Autowired
    private BannedIpDao bannedIpDao;

    @Autowired
    private BannedUserDao bannedUserDao;

    @Autowired
    private UserService userService;



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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String adminSchoolMail = authentication.getName();

        if(CheckIfIpAlreadyBanned(ip)){
            return new ErrorResult(Messages.ipAlreadyBanned);
        }

        DataResult<User> adminUserResult = userService.getUserBySchoolMail(adminSchoolMail);
        if (!adminUserResult.isSuccess()){
            return adminUserResult;
        }

        BannedIp bannedIp = BannedIp.builder()
                .bannedBy(adminUserResult.getData())
                .bannedAt(new Date())
                .ip(ip)
                .reason(reason)
                .build();

        bannedIpDao.save(bannedIp);

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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String adminSchoolMail = authentication.getName();

        if(CheckIfUserAlreadyBanned(schoolMail)){
            return new ErrorResult(Messages.userAlreadyBanned);
        }


        DataResult<User> adminUserResult = userService.getUserBySchoolMail(adminSchoolMail);

        DataResult<User> userToBanResult = userService.getUserBySchoolMail(schoolMail);

        if (!userToBanResult.isSuccess()){
            return userToBanResult;
        }

        if (!adminUserResult.isSuccess()){
            return adminUserResult;
        }

        BannedUser bannedUser = BannedUser.builder()
                .bannedUser(userToBanResult.getData())
                .bannedAt(new Date())
                .reason(reason)
                .bannedBy(adminUserResult.getData())
                .build();

        bannedUserDao.save(bannedUser);
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
    public Result unbanIp(int ip) {
        return null;
    }

    @Override
    public Result unbanUser(int userId) {
        return null;
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
