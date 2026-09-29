package com.weblab.rplace.weblab.rplace.business.concretes;

import com.weblab.rplace.weblab.rplace.business.abstracts.BanService;
import com.weblab.rplace.weblab.rplace.business.abstracts.UserService;
import com.weblab.rplace.weblab.rplace.business.abstracts.UserTokenService;
import com.weblab.rplace.weblab.rplace.business.abstracts.WhitelistedMailService;
import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.core.security.RandomTokens;
import com.weblab.rplace.weblab.rplace.core.utilities.mail.EmailService;
import com.weblab.rplace.weblab.rplace.core.utilities.results.*;
import com.weblab.rplace.weblab.rplace.dataAccess.abstracts.UserDao;
import com.weblab.rplace.weblab.rplace.entities.Role;
import com.weblab.rplace.weblab.rplace.entities.User;
import com.weblab.rplace.weblab.rplace.entities.UserToken;
import com.weblab.rplace.weblab.rplace.entities.UserTokenKind;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class UserManager implements UserService, UserDetailsService {

    private final UserDao userDao;

    private final EmailService emailService;

    private final WhitelistedMailService whiteListedMailService;

    private final UserTokenService userTokenService;

    private final BanService banService;

    @Value("${school.mail.enabled}")
    private Boolean isSchoolMailEnabled;

    public UserManager(EmailService emailService, UserDao userDao, WhitelistedMailService whiteListedMailService,@Lazy UserTokenService userTokenService, @Lazy BanService banService) {
        this.emailService = emailService;
        this.userDao = userDao;
        this.whiteListedMailService = whiteListedMailService;
        this.userTokenService = userTokenService;
        this.banService = banService;
    }

    @Override
    public Result registerUser(String schoolMail, String ipAddress) {

        schoolMail = User.normalizeSchoolMail(schoolMail);

        if(!isSchoolMailAllowed(schoolMail)){
            return new ErrorResult(Messages.invalidSchoolMail);
        }

        /*
        if(CheckIfMailCorrect(schoolMail)){
            return new ErrorResult(Messages.invalidSchoolMail);
        }

         */

        if(!CheckIfMaxTokenCountReachedByIp(ipAddress).isSuccess()){
            return new ErrorResult(Messages.maxTokenCountReachedByIp);
        }

        if (!CheckIfMaxTokenCountReachedBySchoolMail(schoolMail).isSuccess()) {
            return new ErrorResult(Messages.maxTokenCountReachedByUser);
        }

       var userBanResult = banService.isUserBanned(schoolMail);
        if(userBanResult.isSuccess()){
           return new ErrorDataResult<>(userBanResult.getData(), userBanResult.getMessage());

        }


        User user = findOrCreateUser(schoolMail);

        String token = RandomTokens.generate();

        String body= "<body style=\"margin:10px;padding:0 20px;font-family:Arial,sans-serif;background-color:#f8f8f8\">\n" +
                "<div style=\"padding:0 20px;border:2px solid #000;box-shadow:8px 8px 0 rgba(0,0,0,.75);background-color:#fff\">\n" +
                "<h1>SKY LAB YıldızPlace Katılım Bağlantısı</h1>\n" +
                "<p>Aşağıdaki butona tıklayarak etkinliğimize katılabilir ve topluluğumuzun renkli dünyasına adım atabilirsiniz. Her birinizin katkısı bizim için önemli!</p>\n" +
                "<a href=\"https://place.yildizskylab.com/play?token="+token+"\" target=_blank style=\"display:inline-block;background-color:#fd4509;color:#fff;text-decoration:none;font-size:16px;margin-bottom:6px;padding:15px 30px;border:2px solid #000;box-shadow:8px 8px 0 rgba(0,0,0,.75)\">Katılmak için Tıkla</a>\n" +
                "<p style=font-size:12px>Buton çalışmıyor ise bu <a href=\"https://place.yildizskylab.com/play?token="+token+"\">link</a> üzerinden katılabilirsiniz. <br>Unutmayınız, link kişiye özeldir. <b>Kimse ile paylaşmayınız.<b></b></p>\n" +
                "</div>\n" +
                "</body>";
         var mailResult = emailService.sendMail(schoolMail, "YıldızPlace Giriş Bağlantısı", body);
         if (!mailResult.isSuccess()) {
             return mailResult;
         }

        /*
        var userToken = new UserToken().builder().token(token).user(user).build();
        userTokenService.addToken(userToken);
         */

        UserToken userToken = new UserToken().builder()
                .token(token)
                .userId(user.getId())
                .isUsed(false)
                .createdAt(new Date())
                .userIp(ipAddress)
                .kind(UserTokenKind.LINK)
                .build();

        userTokenService.addToken(userToken);

        return new SuccessResult(Messages.registrationSuccessful);

    }

    @Override
    public DataResult<User> logInWithLink(String linkToken) {
        var linkResult = userTokenService.useLoginLink(linkToken);

        if (!linkResult.isSuccess()) {
            return new ErrorDataResult<>(linkResult.getMessage());
        }

        var userResult = getUserById(linkResult.getData().getUserId());
        if (!userResult.isSuccess()) {
            return new ErrorDataResult<>(userResult.getMessage());
        }

        if(!isSchoolMailAllowed(userResult.getData().getSchoolMail())){
            return new ErrorDataResult<>(Messages.invalidSchoolMail);
        }

        return new SuccessDataResult<>(userResult.getData(), Messages.loginSuccess);
    }

    @Override
    public boolean isSchoolMailAllowed(String schoolMail) {
        return !isSchoolMailEnabled || CheckIfSchoolMailCorrect(schoolMail);
    }

    // Serialized: school_mail has no unique constraint, and silent e-skylab logins in two tabs
    // can arrive together for a new person. Place runs as a single instance.
    @Override
    public synchronized User findOrCreateUser(String schoolMail) {
        User user = userDao.findBySchoolMail(schoolMail);
        if(user == null){
            user = new User();
            user.setAuthorities(Set.of(Role.ROLE_USER));
            user.setLastPlacedAt(null);
            user.setSchoolMail(schoolMail);

            addUser(user);
        }
        return user;
    }

    private boolean CheckIfMailCorrect(String schoolMail) {

        if(schoolMail.contains("@std.yildiz.edu.tr") || whiteListedMailService.existsByMail(schoolMail).isSuccess()){
            return false;
        }
        return true;
    }

    private Result CheckIfMaxTokenCountReachedBySchoolMail(String schoolMail) {
        var maxTokenCountByUserPerHour = 5;

        var result = userTokenService.getLoginLinksBetweenDatesBySchoolMail(new Date(System.currentTimeMillis() - 3600000),
                new Date(), schoolMail);

        if (result.getData() == null) {
           return new SuccessDataResult<>();
        }

        if (result.getData().size() >= maxTokenCountByUserPerHour) {
            return new ErrorResult(Messages.maxTokenCountReachedByUser);
        } else {
            return new SuccessResult();
        }
    }

    private Result CheckIfMaxTokenCountReachedByIp(String ipAddress) {
        var maxTokenCountByIpPerHour = 100;

        var result = userTokenService.getLoginLinksBetweenDatesByIp(new Date(System.currentTimeMillis() - 3600000),
                new Date(), ipAddress);

        if (result.getData() == null) {
            return new SuccessDataResult<>();
        }

        if (result.getData().size() >= maxTokenCountByIpPerHour) {
            return new ErrorResult(Messages.maxTokenCountReachedByIp);
        } else {
            return new SuccessResult();
        }
    }

    private boolean CheckIfSchoolMailCorrect(String schoolMail) {
        if (schoolMail == null || schoolMail.isEmpty()) {
            return false;
        }

        String regex = "^[a-z0-9.]+@std\\.yildiz\\.edu\\.tr$";

        return Pattern.matches(regex, schoolMail.toLowerCase());
    }

    @Override
    public Result addUser(User user){
        userDao.save(user);
        return new SuccessResult(Messages.userSuccessfullyAdded);
    }

    @Override
    public DataResult<User> getUserById(int id) {
        User result = userDao.findById(id);

        if (result == null) {
            return new ErrorDataResult<>(Messages.userNotFound);
        }

        return new SuccessDataResult<>(result, Messages.userFound);
    }

    @Override
    public DataResult<User> getUserBySchoolMail(String schoolMail) {
        User result = userDao.findBySchoolMail(schoolMail);

        if (result == null) {
            return new ErrorDataResult<>(Messages.userNotFound);
        }

        return new SuccessDataResult<>(result, Messages.userFound);
    }


    @Override
    public DataResult<User> getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String usersSchoolMail = authentication.getName();
        return getUserBySchoolMail(usersSchoolMail);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userDao.findBySchoolMail(username);
    }


}
