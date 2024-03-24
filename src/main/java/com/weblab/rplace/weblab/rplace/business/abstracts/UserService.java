package com.weblab.rplace.weblab.rplace.business.abstracts;

import com.weblab.rplace.weblab.rplace.core.utilities.results.DataResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.entities.User;
import com.weblab.rplace.weblab.rplace.entities.UserToken;
import org.springframework.security.core.userdetails.UserDetailsService;

import javax.xml.crypto.Data;

public interface UserService extends UserDetailsService {

    Result registerUser(String schoolMail, String ipAddress);

    Result addUser(User user);

    DataResult<User> getUserById(int id);

    DataResult<User> getUserBySchoolMail(String schoolMail);

    Result addModerator(String schoolMail);

    Result removeModerator(String schoolMail);

}
