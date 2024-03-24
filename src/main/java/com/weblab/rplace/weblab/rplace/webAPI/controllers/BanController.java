package com.weblab.rplace.weblab.rplace.webAPI.controllers;

import com.weblab.rplace.weblab.rplace.business.abstracts.BanService;
import com.weblab.rplace.weblab.rplace.core.utilities.results.DataResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.entities.BannedIp;
import com.weblab.rplace.weblab.rplace.entities.BannedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.xml.crypto.Data;
import java.util.List;

@RestController
@RequestMapping("api/bans")
public class BanController {

    @Autowired
    private BanService banService;


    @PostMapping("/banUser")
    public Result banUser(@RequestParam String schoolMail, @RequestParam String reason) {
        return banService.banUser(schoolMail, reason);
    }

    @PostMapping("/banIp")
    public Result banIp(@RequestParam String ip, @RequestParam String reason) {
        return banService.banIp(ip, reason);
    }

    @GetMapping("/getBannedIps")
    public DataResult<List<BannedIp>> getBannedIps() {
        return banService.getBannedIps();
    }

    @GetMapping("/getBannedUsers")
    public DataResult<List<BannedUser>> getBannedUsers() {
        return banService.getBannedUsers();
    }

    @GetMapping("/isIpBanned")
    public DataResult<BannedIp> isIpBanned(@RequestParam String ip) {
        return banService.isIpBanned(ip);
    }

    @GetMapping("/isUserBanned")
    public DataResult<BannedUser> isUserBanned(@RequestParam String schoolMail) {
        return banService.isUserBanned(schoolMail);
    }



}
