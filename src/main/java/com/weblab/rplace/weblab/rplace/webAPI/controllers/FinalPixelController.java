package com.weblab.rplace.weblab.rplace.webAPI.controllers;

import com.weblab.rplace.weblab.rplace.business.abstracts.FinalPixelService;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/finalPixels")
public class FinalPixelController {

    private final FinalPixelService finalPixelService;

    public FinalPixelController(FinalPixelService finalPixelService) {
        this.finalPixelService = finalPixelService;
    }


}
