package com.weblab.rplace.weblab.rplace.business.concretes;

import com.weblab.rplace.weblab.rplace.business.abstracts.EmailService;
import com.weblab.rplace.weblab.rplace.business.abstracts.UserService;
import com.weblab.rplace.weblab.rplace.business.abstracts.UserTokenService;
import com.weblab.rplace.weblab.rplace.business.abstracts.WhitelistedMailService;
import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.core.utilities.results.*;
import com.weblab.rplace.weblab.rplace.dataAccess.abstracts.UserDao;
import com.weblab.rplace.weblab.rplace.entities.Role;
import com.weblab.rplace.weblab.rplace.entities.User;
import com.weblab.rplace.weblab.rplace.entities.UserToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Service
public class UserManager implements UserService, UserDetailsService {

    @Autowired
    private UserDao userDao;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserTokenService userTokenService;

    @Autowired
    private WhitelistedMailService whiteListedMailService;

    @Override
    public Result registerUser(String schoolMail, String ipAddress) {

        /*
        if(!CheckIfSchoolMailCorrect(schoolMail)){
            return new ErrorResult(Messages.invalidSchoolMail);
        }

         */

        if(CheckIfMailCorrect(schoolMail)){
            return new ErrorResult(Messages.invalidSchoolMail);
        }

        if(!CheckIfMaxTokenCountReachedByIp(ipAddress).isSuccess()){
            return new ErrorResult(Messages.maxTokenCountReachedByIp);
        }

        if (!CheckIfMaxTokenCountReachedBySchoolMail(schoolMail).isSuccess()) {
            return new ErrorResult(Messages.maxTokenCountReachedByUser);
        }


      User user = userDao.findBySchoolMail(schoolMail);
        if(user == null){
            user = new User();
            user.setAuthorities(Set.of(Role.ROLE_USER));
            user.setLastPlacedAt(null);
            user.setSchoolMail(schoolMail);

            addUser(user);
        }

        String token = generateToken();

        String body= "<body style=\"margin:10px;padding:0 20px;font-family:Arial,sans-serif;background-color:#f8f8f8\">\n" +
                "<div style=\"padding:0 20px;border:2px solid #000;box-shadow:8px 8px 0 rgba(0,0,0,.75);background-color:#fff\">\n" +
                "<h1>YıldızPlace Katılım Bağlantısı</h1>\n" +
                "<p>Aşağıdaki butona tıklayarak etkinliğimize katılabilir ve topluluğumuzun renkli dünyasına adım atabilirsiniz. Her birinizin katkısı bizim için önemli!</p>\n" +
                "<a href=\"https://place.yildizskylab.com/play?token="+token+"\" target=_blank style=\"display:inline-block;background-color:#fd4509;color:#fff;text-decoration:none;font-size:16px;margin-bottom:6px;padding:15px 30px;border:2px solid #000;box-shadow:8px 8px 0 rgba(0,0,0,.75)\">Katılmak için Tıkla</a>\n" +
                "<p style=font-size:12px>Buton çalışmıyor ise bu <a href=\"https://place.yildizskylab.com/play?token="+token+"\">link</a> üzerinden katılabilirsiniz. <br>Unutmayınız, link kişiye özeldir. <b>Kimse ile paylaşmayınız.<b></b></p>\n" +
                "</div>\n" +
                "</body>";
        emailService.sendEmailWithJavaMailSender(schoolMail, "YıldızPlace Giriş Bağlantısı", body);

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
                .build();

        userTokenService.addToken(userToken);

        return new SuccessResult(Messages.registrationSuccessful);

    }

    private boolean CheckIfMailCorrect(String schoolMail) {

        if(schoolMail.contains("@std.yildiz.edu.tr") || whiteListedMailService.existsByMail(schoolMail).isSuccess()){
            return false;
        }
        return true;
    }

    private Result CheckIfMaxTokenCountReachedBySchoolMail(String schoolMail) {
        var maxTokenCountByUserPerHour = 5;

        var result = userTokenService.getTokensBetweenDatesBySchoolMail(new Date(System.currentTimeMillis() - 3600000),
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

        var result = userTokenService.getTokensBetweenDatesByIp(new Date(System.currentTimeMillis() - 3600000),
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
        return schoolMail.endsWith("@std.yildiz.edu.tr");
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
    public Result addModerator(String schoolMail) {
        if(!CheckIfSchoolMailCorrect(schoolMail)){
            return new ErrorResult(Messages.invalidSchoolMail);
        }

        var adminToAddResult = getUserBySchoolMail(schoolMail);

        if(!adminToAddResult.isSuccess()){
           return new ErrorResult(Messages.userDoesNotExist);
        }

        if(adminToAddResult.getData().getAuthorities().contains(Role.ROLE_MODERATOR)){
            return new ErrorResult(Messages.userAlreadyModerator);
        }

        var user = adminToAddResult.getData();
        user.addRole(Role.ROLE_MODERATOR);
        userDao.save(user);
        return new SuccessResult(Messages.moderatorAdded);


    }

    @Override
    public Result removeModerator(String schoolMail) {
        if(!CheckIfSchoolMailCorrect(schoolMail)){
            return new ErrorResult(Messages.invalidSchoolMail);
        }

        var adminToRemoveResult = getUserBySchoolMail(schoolMail);

        if(!adminToRemoveResult.isSuccess()){
            return new ErrorResult(Messages.userDoesNotExist);
        }

        if(!adminToRemoveResult.getData().getAuthorities().contains(Role.ROLE_MODERATOR)){
            return new ErrorResult(Messages.userNotModerator);
        }

        var user = adminToRemoveResult.getData();
        user.getAuthorities().remove(Role.ROLE_MODERATOR);
        userDao.save(user);
        return new SuccessResult(Messages.moderatorRemoved);
    }


    private String generateToken() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().encodeToString(randomBytes);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userDao.findBySchoolMail(username);
    }


}
