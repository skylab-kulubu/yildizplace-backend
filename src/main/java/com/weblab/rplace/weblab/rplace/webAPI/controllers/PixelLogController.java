package com.weblab.rplace.weblab.rplace.webAPI.controllers;

import com.weblab.rplace.weblab.rplace.business.abstracts.PixelLogService;
import com.weblab.rplace.weblab.rplace.core.utilities.results.DataResult;
import com.weblab.rplace.weblab.rplace.entities.PixelLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.util.List;

@RestController
@RequestMapping("api/pixelLogs")
@RequiredArgsConstructor
public class PixelLogController {

    private final PixelLogService pixelLogService;

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
