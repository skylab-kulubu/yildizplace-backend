package com.weblab.rplace.weblab.rplace.webAPI.controllers;

import com.weblab.rplace.weblab.rplace.business.abstracts.UserService;
import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.core.security.ClientIp;
import com.weblab.rplace.weblab.rplace.core.security.LoginMode;
import com.weblab.rplace.weblab.rplace.core.security.PlaceSessions;
import com.weblab.rplace.weblab.rplace.core.utilities.results.ErrorResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.core.utilities.results.SuccessResult;
import com.weblab.rplace.weblab.rplace.entities.dtos.RegisterRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("api/users")
@RequiredArgsConstructor
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    // Requests that still carry the address or the link in the query string, where access logs,
    // browser history and Referer headers keep it. Counted and logged without the value; the GET
    // form goes once the frontend sends POST bodies (ticket 07).
    private final AtomicLong registerQueryStringUses = new AtomicLong();
    private final AtomicLong loginQueryStringUses = new AtomicLong();

    private final UserService userService;

    private final PlaceSessions placeSessions;

    private final LoginMode loginMode;

    private final ClientIp clientIp;

    // POST with a JSON body {"schoolMail": ...}. The live frontend still asks with GET ?schoolMail=, which
    // keeps working for now: counted in the log (never the address) and answered with a Deprecation header.
    @RequestMapping(value = "/register", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Result> registerUser(@RequestBody(required = false) RegisterRequestDto registerRequestDto,
                                               @RequestParam(required = false) String schoolMail,
                                               HttpServletRequest request, HttpServletResponse response){
        if (!loginMode.isMailLoginOpen()) {
            return mailLoginClosed();
        }

        if (registerRequestDto != null && registerRequestDto.getSchoolMail() != null) {
            schoolMail = registerRequestDto.getSchoolMail();
        } else if (schoolMail != null && request.getQueryString() != null && request.getQueryString().contains("schoolMail=")) {
            noteQueryStringUse(response, "register", "the school address", registerQueryStringUses);
        }
        if (schoolMail == null || schoolMail.isBlank()) {
            return ResponseEntity.ok(new ErrorResult(Messages.invalidSchoolMail));
        }

        return ResponseEntity.ok(userService.registerUser(schoolMail, clientIp.of(request)));
    }

    // POST with the token as a form field. The live frontend opens the mailed link with GET ?token=, which
    // keeps working for now: counted in the log (never the token) and answered with a Deprecation header.
    @RequestMapping(value = "/login", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Result> loginUser(@RequestParam String token, HttpServletRequest request, HttpServletResponse response){
        if (!loginMode.isMailLoginOpen()) {
            return mailLoginClosed();
        }

        if (request.getQueryString() != null && request.getQueryString().contains("token=")) {
            noteQueryStringUse(response, "login", "the login link", loginQueryStringUses);
        }

        var userResult = userService.logInWithLink(token, clientIp.of(request));

        if (!userResult.isSuccess()){
            return ResponseEntity.ok(new ErrorResult(userResult.getMessage()));
        }

        placeSessions.openMailSession(userResult.getData(), response);

        return ResponseEntity.ok(new SuccessResult(Messages.loginSuccess));
    }

    private static void noteQueryStringUse(HttpServletResponse response, String endpoint, String what, AtomicLong uses) {
        log.info("/api/users/{}: {} came in the query string ({} since start); the frontend should send it in a POST body",
                endpoint, what, uses.incrementAndGet());
        response.setHeader("Deprecation", "true");
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
