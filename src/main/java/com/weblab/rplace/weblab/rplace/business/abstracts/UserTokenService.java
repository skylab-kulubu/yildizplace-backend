package com.weblab.rplace.weblab.rplace.business.abstracts;

import com.weblab.rplace.weblab.rplace.core.utilities.results.DataResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.entities.UserToken;
import com.weblab.rplace.weblab.rplace.entities.dtos.TokenExtendRequestDto;
import com.weblab.rplace.weblab.rplace.entities.dtos.TokenExtendResponseDto;

import java.util.Date;
import java.util.List;

public interface UserTokenService {

    DataResult<UserToken> getUserToken(String token);

    Result addToken(UserToken userToken);

    DataResult<List<UserToken>> getAll();

    DataResult<String> getUserNameBySessionToken(String token);

    DataResult<UserToken> useLoginLink(String token);

    // Ends the session with this value; login links and unknown values are left alone.
    Result endSession(String sessionToken);

    DataResult<List<UserToken>> getLoginLinksBetweenDatesByIp(Date startDate, Date endDate , String ipAddress);

    DataResult<List<UserToken>> getLoginLinksBetweenDatesBySchoolMail(Date startDate, Date endDate , String schoolMail);

    DataResult<TokenExtendResponseDto> extendToken(TokenExtendRequestDto tokenVerifyRequestDto);

    DataResult<UserToken> getAuthenticatedUsersToken();

}
