package com.weblab.rplace.weblab.rplace.webAPI.controllers;

import com.weblab.rplace.weblab.rplace.business.abstracts.UserService;
import com.weblab.rplace.weblab.rplace.business.abstracts.UserTokenService;
import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.core.utilities.results.ErrorResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.core.utilities.results.SuccessResult;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/users")
//@CrossOrigin(origins = {"http://yildizrplacetest.vercel.app","https://yildizrplacetest.vercel.app","http://localhost:3000"})
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserTokenService userTokenService;

    @GetMapping("/register")
    public Result registerUser(@RequestParam String schoolMail, HttpServletRequest request){
        String ipAddress = request.getRemoteAddr();

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            ipAddress = forwardedFor.split(",")[0];
        }

        return userService.registerUser(schoolMail, ipAddress);
    }

    @GetMapping("/login")
    public Result loginUser(@RequestParam String token, HttpServletResponse response){
        var tokenResult = userTokenService.validateToken(token);

        if (!tokenResult.isSuccess()){
            return new ErrorResult(tokenResult.getMessage());
        }

        if(tokenResult.isSuccess()){
            //response.setHeader("Set-Cookie", "user_token="+token+"; SameSite=strict; Secure; HttpOnly; Path=/; Domain=egehan.dev; Max-Age=31536000");

            var cookie = new Cookie("user_token", token);
            cookie.setPath("/");
            cookie.setDomain("place.yildizskylab.com");
            cookie.setMaxAge(31536000);
            cookie.setHttpOnly(true);
            cookie.setSecure(true);

            response.addCookie(cookie);

            var userRolesResult = userTokenService.getUserRolesByToken(token);

            //System.out.println(userRolesResult.getData());

            if(userRolesResult.getData().contains("ROLE_ADMIN") || userRolesResult.getData().contains("ROLE_MODERATOR")){
                var adminCookie = new Cookie("isAdmin", "true");
                adminCookie.setPath("/");
                adminCookie.setDomain("place.yildizskylab.com");
                adminCookie.setMaxAge(31536000);
                adminCookie.setHttpOnly(true);
                adminCookie.setSecure(true);

                //System.out.println(adminCookie.getName() + adminCookie.getValue());

                response.addCookie(adminCookie);
            }
            //

            //response.setHeader("Set-Cookie", "user_token="+token+"; SameSite=strict; Secure; HttpOnly; Path=/; Domain=localhost; Max-Age=31536000");


            return new SuccessResult(Messages.loginSuccess);
        }

        return new ErrorResult(Messages.loginFailed);

    }

    @GetMapping("/logout")
    public Result logoutUser(HttpServletResponse response){
        var cookie = new Cookie("user_token", "");
        cookie.setPath("/");
        cookie.setDomain("place.yildizskylab.com");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);

        response.addCookie(cookie);

        var adminCookie = new Cookie("isAdmin", "");
        adminCookie.setPath("/");
        adminCookie.setDomain("place.yildizskylab.com");
        adminCookie.setMaxAge(0);
        adminCookie.setHttpOnly(true);
        adminCookie.setSecure(true);

        response.addCookie(adminCookie);

        return new SuccessResult(Messages.logoutSuccess);
    }


    @PostMapping("/addModerator")
    public Result addModerator(@RequestParam String schoolMail){
        return userService.addModerator(schoolMail);
    }

    @PostMapping("/removeModerator")
    public Result removeModerator(@RequestParam String schoolMail){
        return userService.removeModerator(schoolMail);
    }


}
