package com.weblab.rplace.weblab.rplace.webAPI.controllers;

import com.weblab.rplace.weblab.rplace.business.abstracts.WhitelistedMailService;
import com.weblab.rplace.weblab.rplace.core.utilities.results.ErrorResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.entities.dtos.WhitelistedMailDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/whitelistedMails")
@RequiredArgsConstructor
public class WhitelistedMailController {

    private final WhitelistedMailService whitelistedMailService;


    @PostMapping("/add")
    public Result add(@RequestBody WhitelistedMailDto whitelistedMailDto) {

        if (whitelistedMailDto.getKey().equals("whitelist için key")){
            var result = whitelistedMailService.add(whitelistedMailDto.getMail());
            return result;
        }

        return new ErrorResult("Key is not valid");




    }

}
