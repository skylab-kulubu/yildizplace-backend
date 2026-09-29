package com.weblab.rplace.weblab.rplace.core.configs;

import com.weblab.rplace.weblab.rplace.core.security.LoginMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoginModeConfig {

    // An unknown PLACE_LOGIN_MODE stops the application from starting.
    @Bean
    public LoginMode loginMode(@Value("${place.login.mode}") String mode) {
        return LoginMode.fromValue(mode);
    }
}
