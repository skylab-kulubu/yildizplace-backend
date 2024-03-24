package com.weblab.rplace.weblab.rplace.webAPI.controllers;

import com.weblab.rplace.weblab.rplace.business.abstracts.UserTokenService;
import com.weblab.rplace.weblab.rplace.core.utilities.results.DataResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.entities.UserToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.Mapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.xml.crypto.Data;
import java.util.List;

@RestController
@RequestMapping("api/userTokens")
public class UserTokenController {

    private UserTokenService userTokenService;

    @Autowired
    public UserTokenController(UserTokenService userTokenService) {
        this.userTokenService = userTokenService;
    }

    @GetMapping("/getUserToken")
    public DataResult<UserToken> getUserToken(String userToken){
        return userTokenService.getUserToken(userToken);
    }

    @GetMapping("/getAll")
    public DataResult<List<UserToken>> getAll(){
        return userTokenService.getAll();
    }
}
