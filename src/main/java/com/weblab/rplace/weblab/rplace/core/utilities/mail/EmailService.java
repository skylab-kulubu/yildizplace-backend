package com.weblab.rplace.weblab.rplace.core.utilities.mail;

import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;

public interface EmailService {

    Result sendMail(String to, String subject, String body);

}
