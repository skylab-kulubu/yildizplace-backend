package com.weblab.rplace.weblab.rplace.webAPI.controllers;

import com.weblab.rplace.weblab.rplace.business.abstracts.UserTokenService;
import com.weblab.rplace.weblab.rplace.core.utilities.results.DataResult;
import com.weblab.rplace.weblab.rplace.entities.UserToken;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/userTokens")
@RequiredArgsConstructor
public class UserTokenController {

    private final UserTokenService userTokenService;


    @GetMapping("/getUserToken")
    public DataResult<UserToken> getUserToken(String userToken){
        return userTokenService.getUserToken(userToken);
    }

    @GetMapping("/getAll")
    public DataResult<List<UserToken>> getAll(){
        return userTokenService.getAll();
    }
}
