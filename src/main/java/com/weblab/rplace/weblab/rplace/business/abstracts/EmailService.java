package com.weblab.rplace.weblab.rplace.business.abstracts;

import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;

public interface EmailService {

    Result sendEmailWithJavaMailSender(String to, String subject, String body);

    Result sendEmailWithResend(String to, String subject, String body);

}
