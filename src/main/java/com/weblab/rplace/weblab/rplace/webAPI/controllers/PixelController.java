package com.weblab.rplace.weblab.rplace.webAPI.controllers;

import java.util.List;

import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.entities.dtos.FillDto;
import com.weblab.rplace.weblab.rplace.entities.dtos.PixelDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import com.weblab.rplace.weblab.rplace.business.abstracts.PixelService;
import com.weblab.rplace.weblab.rplace.core.utilities.results.DataResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.entities.Pixel;

@RestController
@RequestMapping("api/pixels")
@RequiredArgsConstructor
public class PixelController {
	
	private final PixelService pixelService;

	private final SimpMessagingTemplate messagingTemplate;
	
	
	@PostMapping("/addPixel")
	public ResponseEntity<Result> addPixel(@RequestBody Pixel pixel, HttpServletRequest request) {
		String ipAddress = request.getRemoteAddr();

		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isEmpty()) {
			ipAddress = forwardedFor.split(",")[0];
		}


		var result = pixelService.addPixel(pixel, ipAddress);

		if (result.isSuccess()){
			messagingTemplate.convertAndSend("/topic/pixels",pixel);
		}

		if (result.getMessage().equals(Messages.invalidSchoolMailToAddPixel)){
			return ResponseEntity.status(403).body(result);
		}

		return ResponseEntity.ok(result);


	}


	@GetMapping("/getBoard")
	public DataResult<List<Pixel>> getBoard(){
		return pixelService.getBoard();
	}

	@GetMapping("/getColors")
	public DataResult<List<String>> getColorsMatris(){
		return pixelService.getColors();
	}

	@GetMapping("getByXAndY")
	public DataResult<Pixel> getByXAndY(@RequestParam int x, int y){
		return pixelService.getByXAndY(x,y);
	}

	@GetMapping("/getPixelsBetweenDates")
	public DataResult<List<PixelDto>> getPixelsBetweenDates(@RequestParam long unixStartDate, @RequestParam long unixEndDate){
		return pixelService.getPixelsBetweenDates(unixStartDate, unixEndDate);
	}


	@PostMapping("/fill")
	public Result fill(@RequestBody FillDto fillDto, HttpServletRequest request) {
		String ipAddress = request.getRemoteAddr();

		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isEmpty()) {
			ipAddress = forwardedFor.split(",")[0];
		}

		var result = pixelService.fill(fillDto, ipAddress);
		if (result.isSuccess()){
			messagingTemplate.convertAndSend("/topic/fill",fillDto);
		}

		return result;

	}

	@PostMapping("/bringBackPixels")
	public Result bringBackPixels(@RequestBody FillDto fillDto, HttpServletRequest request) {
		String ipAddress = request.getRemoteAddr();

		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isEmpty()) {
			ipAddress = forwardedFor.split(",")[0];
		}

		var result = pixelService.bringBackPreviousPixels(fillDto, ipAddress);
		if (result.isSuccess()){
			messagingTemplate.convertAndSend("/topic/fill",fillDto);
		}

		return result;
	}

}



