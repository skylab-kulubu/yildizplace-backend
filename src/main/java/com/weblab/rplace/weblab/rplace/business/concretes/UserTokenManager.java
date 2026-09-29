package com.weblab.rplace.weblab.rplace.business.concretes;

import com.weblab.rplace.weblab.rplace.business.abstracts.UserService;
import com.weblab.rplace.weblab.rplace.business.abstracts.UserTokenService;
import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.core.utilities.results.*;
import com.weblab.rplace.weblab.rplace.core.utilities.turnstile.TurnstileService;
import com.weblab.rplace.weblab.rplace.dataAccess.abstracts.UserTokenDao;
import com.weblab.rplace.weblab.rplace.entities.User;
import com.weblab.rplace.weblab.rplace.entities.UserToken;
import com.weblab.rplace.weblab.rplace.entities.UserTokenKind;
import com.weblab.rplace.weblab.rplace.entities.dtos.TokenExtendRequestDto;
import com.weblab.rplace.weblab.rplace.entities.dtos.TokenExtendResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
public class UserTokenManager implements UserTokenService {

    private final UserTokenDao userTokenDao;

    private final UserService userService;

    private final TurnstileService turnstileService;

    private final Clock clock;

    @Value("${place.login.link-ttl}")
    private Duration loginLinkTtl;

    // PLACE_LOGIN_LINK_SINGLE_USE; false lets a link log in again until it expires.
    @Value("${place.login.link-single-use}")
    private boolean loginLinkSingleUse;

    public UserTokenManager(@Lazy UserService userService, UserTokenDao userTokenDao, TurnstileService turnstileService, Clock clock) {
        this.userService = userService;
        this.userTokenDao = userTokenDao;
        this.turnstileService = turnstileService;
        this.clock = clock;
    }

    @Override
    public DataResult<UserToken> getUserToken(String token) {
        UserToken userToken = userTokenDao.findByToken(token);

        if (userToken == null) {
            return new ErrorDataResult(Messages.tokenNotFound);
        }

        return new SuccessDataResult<UserToken>(userToken, Messages.tokenFound);
    }

    @Override
    public Result addToken(UserToken userToken) {
        userTokenDao.save(userToken);
        return new SuccessResult(Messages.tokenAdded);
    }

    @Override
    public DataResult<List<UserToken>> getAll() {
        return new SuccessDataResult<List<UserToken>>(userTokenDao.findAll(), Messages.tokenListed);
    }

    @Override
    public DataResult<String> getUserNameBySessionToken(String token) {
        UserToken result = userTokenDao.findSessionByToken(token);

        if (result == null) {
            return new ErrorDataResult<String>(null, Messages.tokenNotFound);
        }

        var usernameResult = userService.getUserById(result.getUserId());

        if (!usernameResult.isSuccess()){
            return new ErrorDataResult<String>(null,Messages.userNotFound);
        }

        String username = usernameResult.getData().getSchoolMail();

        return new SuccessDataResult<String>(username, Messages.tokenFound);

    }

    @Override
    public DataResult<UserToken> useLoginLink(String token) {
        Date now = Date.from(clock.instant());
        Date createdAfter = new Date(now.getTime() - loginLinkTtl.toMillis());

        if (userTokenDao.useLink(token, now, createdAfter, !loginLinkSingleUse) == 0) {
            return new ErrorDataResult<>(Messages.loginLinkInvalid);
        }

        return new SuccessDataResult<>(userTokenDao.findByToken(token), Messages.tokenFound);
    }

    @Override
    public Result endSession(String sessionToken) {
        userTokenDao.deleteSession(sessionToken);
        return new SuccessResult();
    }

    @Override
    public DataResult<List<UserToken>> getLoginLinksBetweenDatesByIp(Date startDate, Date endDate ,String ipAddress) {
        List<UserToken> result = userTokenDao.findAllByCreatedAtBetweenAndUserIpAndKind(startDate, endDate, ipAddress, UserTokenKind.LINK);

        if (result == null) {
            return new ErrorDataResult<List<UserToken>>(Messages.tokenNotFound);
        }

        return new SuccessDataResult<List<UserToken>>(result, Messages.tokenFound);
    }

    @Override
    public DataResult<List<UserToken>> getLoginLinksBetweenDatesBySchoolMail(Date startDate, Date endDate, String schoolMail) {
        DataResult<User> userResult = userService.getUserBySchoolMail(schoolMail);

        if (!userResult.isSuccess()) {
            return new ErrorDataResult<List<UserToken>>(userResult.getMessage());
        }


        List<UserToken> result = userTokenDao.findAllByCreatedAtBetweenAndUserIdAndKind(startDate, endDate, userResult.getData().getId(), UserTokenKind.LINK);

        if (result == null) {
            return new ErrorDataResult<List<UserToken>>(Messages.tokenNotFound);
        }

        return new SuccessDataResult<List<UserToken>>(result, Messages.tokenFound);
    }

    @Override
    public DataResult<TokenExtendResponseDto> extendToken(TokenExtendRequestDto tokenExtendRequestDto) {

        var authTokenResult = getAuthenticatedUsersToken();
        if (!authTokenResult.isSuccess()) {
            return new ErrorDataResult<>(Messages.tokenNotFound);
        }

        var userToken = authTokenResult.getData();

        if (userToken == null){
            return new ErrorDataResult<>(Messages.tokenNotFound);
        }

        boolean isTurnstileValid = turnstileService.verifyToken(tokenExtendRequestDto.getSecurityToken());

        if (!isTurnstileValid){
            return new ErrorDataResult<>(Messages.turnstileVerificationFailed);
        }

        LocalDateTime newExpiryDate = LocalDateTime.now().plusSeconds(90);
        userToken.setValidUntil(newExpiryDate);
        userToken.setCloudflareToken(tokenExtendRequestDto.getSecurityToken());
        userTokenDao.save(userToken);

        long unixValidUntil = newExpiryDate
                .atZone(ZoneId.systemDefault())
                .toEpochSecond();

        TokenExtendResponseDto responseDto = new TokenExtendResponseDto();
        responseDto.setValidUntil(unixValidUntil);

        return new SuccessDataResult<>(responseDto, Messages.tokenExtended);
    }

    @Override
    public DataResult<UserToken> getAuthenticatedUsersToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getCredentials() != null) {
            String token = auth.getCredentials().toString();
            UserToken userToken = userTokenDao.findByToken(token);
            if (userToken != null) {
                return new SuccessDataResult<UserToken>(userToken, Messages.tokenFound);
            }
        }

        return new ErrorDataResult<UserToken>(Messages.tokenNotFound);
    }


}
