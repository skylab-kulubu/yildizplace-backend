package com.weblab.rplace.weblab.rplace.core.utilities.mail.resendMail;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.SendEmailRequest;
import com.resend.services.emails.model.SendEmailResponse;
import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.core.utilities.mail.EmailService;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.core.utilities.results.SuccessResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

//NOT BEING USED, USING JAVA MAIL SENDER INSTEAD
//@Service
@RequiredArgsConstructor
public class ResendMailManager implements EmailService {

    @Value("${resend.api.key}")
    private final String apiKey;

    @Override
    public Result sendMail(String to, String subject, String body) {
        Resend resend = new Resend(apiKey);

        SendEmailRequest sendEmailRequest = SendEmailRequest.builder()
                .from("Acme <onboarding@resend.dev>")
                .to(to)
                .subject(subject)
                .html(body)
                .build();
        try {
            SendEmailResponse data = resend.emails().send(sendEmailRequest);
            return new SuccessResult(Messages.emailSent);
        } catch (ResendException e) {
            throw new RuntimeException(e);
        }
    }
}
