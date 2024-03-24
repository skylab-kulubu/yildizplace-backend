package com.weblab.rplace.weblab.rplace.business.concretes;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.SendEmailRequest;
import com.resend.services.emails.model.SendEmailResponse;
import com.weblab.rplace.weblab.rplace.business.abstracts.EmailService;
import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.core.utilities.results.ErrorResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.core.utilities.results.SuccessResult;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

@Service
public class EmailManager implements EmailService{

    @Autowired
    private JavaMailSender javaMailSender;

    @Override
    public Result sendEmailWithJavaMailSender(String to, String subject, String body) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
            helper.setFrom("yildizplace@outlook.com");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);

            javaMailSender.send(mimeMessage);
            return new SuccessResult(Messages.emailSent);
        } catch (MessagingException e) {// Handle the exception properly in your application
            return new ErrorResult(Messages.emailNotSent);
        }
    }

    @Override
    public Result sendEmailWithResend(String to, String subject, String body) {
        Resend resend = new Resend("re_SXmy5pbX_EkFBQmF3S2NfJyNyKSWbSoWk");

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
