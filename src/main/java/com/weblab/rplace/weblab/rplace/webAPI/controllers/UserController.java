package com.weblab.rplace.weblab.rplace.webAPI.controllers;

import com.weblab.rplace.weblab.rplace.business.abstracts.UserService;
import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.core.security.LoginMode;
import com.weblab.rplace.weblab.rplace.core.security.PlaceSessions;
import com.weblab.rplace.weblab.rplace.core.utilities.results.ErrorResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.core.utilities.results.SuccessResult;
import com.weblab.rplace.weblab.rplace.entities.dtos.RegisterRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private final PlaceSessions placeSessions;

    private final LoginMode loginMode;

    @PostMapping("/register")
    public ResponseEntity<Result> registerUser(@RequestBody RegisterRequestDto registerRequestDto, HttpServletRequest request){
        if (!loginMode.isMailLoginOpen()) {
            return mailLoginClosed();
        }

        String ipAddress = request.getRemoteAddr();

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            ipAddress = forwardedFor.split(",")[0];
        }

        return ResponseEntity.ok(userService.registerUser(registerRequestDto.getSchoolMail(), ipAddress));
    }

    @PostMapping("/login")
    public ResponseEntity<Result> loginUser(@RequestParam String token, HttpServletResponse response){
        if (!loginMode.isMailLoginOpen()) {
            return mailLoginClosed();
        }

        var userResult = userService.logInWithLink(token);

        if (!userResult.isSuccess()){
            return ResponseEntity.ok(new ErrorResult(userResult.getMessage()));
        }

        placeSessions.openMailSession(userResult.getData(), response);

        return ResponseEntity.ok(new SuccessResult(Messages.loginSuccess));
    }

    private ResponseEntity<Result> mailLoginClosed() {
        return ResponseEntity.status(403).body(new ErrorResult(Messages.mailLoginClosed));
    }

    // Ends the Place session only; the e-skylab session in Keycloak stays (ADR 0060).
    @GetMapping("/logout")
    public Result logoutUser(HttpServletRequest request, HttpServletResponse response){
        placeSessions.end(request, response);

        return new SuccessResult(Messages.logoutSuccess);
    }

}
