package com.weblab.rplace.weblab.rplace.business.abstracts;

import com.weblab.rplace.weblab.rplace.core.utilities.results.DataResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.entities.UserToken;

import java.util.Date;
import java.util.List;

public interface UserTokenService {

    DataResult<UserToken> getUserToken(String token);

    Result addToken(UserToken userToken);

    DataResult<List<UserToken>> getAll();

    DataResult<String> getUserNameByToken(String token);

    DataResult<List<String>> getUserRolesByToken(String token);

    Result validateToken(String token);

    DataResult<List<UserToken>> getTokensBetweenDatesByIp(Date startDate, Date endDate , String ipAddress);

    DataResult<List<UserToken>> getTokensBetweenDatesBySchoolMail(Date startDate, Date endDate , String schoolMail);

}
