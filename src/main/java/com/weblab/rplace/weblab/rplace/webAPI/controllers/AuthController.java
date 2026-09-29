package com.weblab.rplace.weblab.rplace.webAPI.controllers;

import com.weblab.rplace.weblab.rplace.core.security.LoginMode;
import com.weblab.rplace.weblab.rplace.entities.dtos.LoginModeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginMode loginMode;

    // Admins and moderators log in with e-skylab whatever the mode is (ADR 0060).
    @GetMapping("/mode")
    public LoginModeDto getMode() {
        return new LoginModeDto(loginMode.getValue(), LoginMode.ESKYLAB.getValue());
    }
}
