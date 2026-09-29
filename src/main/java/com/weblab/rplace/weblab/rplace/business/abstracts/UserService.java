package com.weblab.rplace.weblab.rplace.business.abstracts;

import com.weblab.rplace.weblab.rplace.core.utilities.results.DataResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.entities.User;
import com.weblab.rplace.weblab.rplace.entities.UserToken;
import org.springframework.security.core.userdetails.UserDetailsService;

import javax.xml.crypto.Data;

public interface UserService extends UserDetailsService {

    Result registerUser(String schoolMail, String ipAddress);

    // The user a mailed login link logs in; the caller opens the session (PlaceSessions).
    DataResult<User> logInWithLink(String linkToken);

    // Whether this (lowercase) address may log in: a school address while SCHOOL_MAIL_ENABLED is on.
    boolean isSchoolMailAllowed(String schoolMail);

    // The account of this (lowercase) school address, opened with ROLE_USER if there is none.
    User findOrCreateUser(String schoolMail);

    Result addUser(User user);

    DataResult<User> getUserById(int id);

    DataResult<User> getUserBySchoolMail(String schoolMail);

    DataResult<User> getAuthenticatedUser();

}
