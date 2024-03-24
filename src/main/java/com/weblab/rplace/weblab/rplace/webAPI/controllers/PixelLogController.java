package com.weblab.rplace.weblab.rplace.webAPI.controllers;

import com.weblab.rplace.weblab.rplace.business.abstracts.PixelLogService;
import com.weblab.rplace.weblab.rplace.core.utilities.results.DataResult;
import com.weblab.rplace.weblab.rplace.entities.PixelLog;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("api/pixelLogs")
//@CrossOrigin(origins = {"http://yildizrplacetest.vercel.app","https://yildizrplacetest.vercel.app","http://localhost:3000"})
public class PixelLogController {
    private PixelLogService pixelLogService;

    @Autowired
    public PixelLogController(PixelLogService pixelLogService) {
        this.pixelLogService = pixelLogService;
    }

    @GetMapping("/getPixelLogsByPixelId")
    DataResult<List<PixelLog>> getPixelLogsByPixelId(@RequestParam int pixelId){
        return pixelLogService.getPixelLogsByPixelId(pixelId);
    }

    @GetMapping("/getPixelLogsByPlacerIp")
    DataResult<List<PixelLog>> getPixelLogsByPlacerIp(@RequestParam String placerIp){
        return pixelLogService.getPixelLogsByPlacerIp(placerIp);
    }

    @GetMapping("/getPixelLogsBySchoolMail")
    DataResult<List<PixelLog>> getPixelLogsBySchoolMail(@RequestParam String schoolMail){
        return pixelLogService.getPixelLogsBySchoolMail(schoolMail);
    }

    @GetMapping("/getPixelLogsByXAndY")
    DataResult<List<PixelLog>> getPixelLogsByXAndY(@RequestParam int x, @RequestParam int y){
        return pixelLogService.getPixelLogsByXAndY(x,y);
    }

    @GetMapping("/getPixelLogs")
    DataResult<List<PixelLog>> getPixelLogs(){
        return pixelLogService.getPixelLogs();
    }



    @GetMapping("/getPixelLogsBetweenDates")
    DataResult<List<PixelLog>> getPixelLogsBetweenDates(@RequestParam long unixStartDate, @RequestParam long unixEndDate) throws ParseException {
        return pixelLogService.getPixelLogsBetweenDates(unixStartDate, unixEndDate);
    }



}
