package com.weblab.rplace.weblab.rplace.business.concretes;

import com.weblab.rplace.weblab.rplace.business.abstracts.UserService;
import com.weblab.rplace.weblab.rplace.business.abstracts.UserTokenService;
import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.core.utilities.results.*;
import com.weblab.rplace.weblab.rplace.core.utilities.turnstile.TurnstileService;
import com.weblab.rplace.weblab.rplace.dataAccess.abstracts.UserTokenDao;
import com.weblab.rplace.weblab.rplace.entities.User;
import com.weblab.rplace.weblab.rplace.entities.UserToken;
import com.weblab.rplace.weblab.rplace.entities.dtos.TokenExtendRequestDto;
import com.weblab.rplace.weblab.rplace.entities.dtos.TokenExtendResponseDto;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Service
public class UserTokenManager implements UserTokenService {

    private final UserTokenDao userTokenDao;

    private final UserService userService;

    private final TurnstileService turnstileService;

    public UserTokenManager(@Lazy UserService userService, UserTokenDao userTokenDao, TurnstileService turnstileService) {
        this.userService = userService;
        this.userTokenDao = userTokenDao;
        this.turnstileService = turnstileService;
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
    public DataResult<String> getUserNameByToken(String token) {
        UserToken result = userTokenDao.findByToken(token);

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
    public DataResult<List<String>> getUserRolesByToken(String token) {

        var userTokenResult = getUserToken(token);

        if (!userTokenResult.isSuccess()){
            return new ErrorDataResult<List<String>>(null, userTokenResult.getMessage());
        }

        var userResult = userService.getUserById(userTokenResult.getData().getUserId());

        if (!userResult.isSuccess()){
            return new ErrorDataResult<List<String>>(null, userResult.getMessage());
        }

        List<String> roles = userResult.getData().getAuthorities().stream().map(authority -> authority.getAuthority()).toList();

        return new SuccessDataResult<List<String>>(roles, Messages.tokenFound);


    }

    @Override
    public Result validateToken(String token) {
        UserToken userToken = userTokenDao.findByToken(token);

        if (userToken == null) {
            return new ErrorResult(Messages.tokenNotFound);
        }


        /*
        if (userToken.isUsed()) {
            return new ErrorResult(Messages.tokenUsed);
        }
         */


        userToken.setUsed(true);
        userToken.setUsedAt(new Date());
        userTokenDao.save(userToken);
        return new SuccessResult(Messages.tokenFound);
    }

    @Override
    public DataResult<List<UserToken>> getTokensBetweenDatesByIp(Date startDate, Date endDate ,String ipAddress) {
        List<UserToken> result = userTokenDao.findAllByCreatedAtBetweenAndUserIp(startDate, endDate, ipAddress);

        if (result == null) {
            return new ErrorDataResult<List<UserToken>>(Messages.tokenNotFound);
        }

        return new SuccessDataResult<List<UserToken>>(result, Messages.tokenFound);
    }

    @Override
    public DataResult<List<UserToken>> getTokensBetweenDatesBySchoolMail(Date startDate, Date endDate, String schoolMail) {
        DataResult<User> userResult = userService.getUserBySchoolMail(schoolMail);

        if (!userResult.isSuccess()) {
            return new ErrorDataResult<List<UserToken>>(userResult.getMessage());
        }


        List<UserToken> result = userTokenDao.findAllByCreatedAtBetweenAndUserId(startDate, endDate, userResult.getData().getId());

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

        LocalDateTime newExpiryDate = LocalDateTime.now().plusMinutes(1);
        userToken.setValidUntil(newExpiryDate);
        userTokenDao.save(userToken);

        long remainingSeconds =
                Duration.between(LocalDateTime.now(), newExpiryDate).getSeconds();

        TokenExtendResponseDto responseDto =
                new TokenExtendResponseDto(remainingSeconds);

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
