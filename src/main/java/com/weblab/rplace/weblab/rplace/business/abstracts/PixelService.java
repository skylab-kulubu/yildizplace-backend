package com.weblab.rplace.weblab.rplace.business.abstracts;

import java.util.List;

import com.weblab.rplace.weblab.rplace.core.utilities.results.DataResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.entities.Pixel;
import com.weblab.rplace.weblab.rplace.entities.dtos.FillDto;
import com.weblab.rplace.weblab.rplace.entities.dtos.PixelDto;
import jakarta.servlet.http.HttpServletRequest;

public interface PixelService  {

	
	DataResult<List<Pixel>> getBoard();

	DataResult<List<String>> getColors();
	
	Result addPixel(Pixel pixel, String  ipAddress);
	
	DataResult<Pixel> getByXAndY(int x, int y);

	DataResult<List<PixelDto>> getPixelsBetweenDates(long unixStartDate, long unixEndDate);

	DataResult<FillDto> fill(FillDto fillDto, String ipAddress);
	
}
