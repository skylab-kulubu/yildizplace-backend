package com.weblab.rplace.weblab.rplace.business.abstracts;

import com.weblab.rplace.weblab.rplace.core.utilities.results.DataResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.entities.Pixel;
import com.weblab.rplace.weblab.rplace.entities.PixelLog;

import java.util.List;

public interface PixelLogService {

    Result addPixelLog(Pixel pixel,String oldColor, String placerIp);

    DataResult<List<PixelLog>> getPixelLogsByPixelId(int pixelId);

    DataResult<List<PixelLog>> getPixelLogsByPlacerIp(String placerIp);

    DataResult<List<PixelLog>> getPixelLogsByXAndY(int x, int y);

    DataResult<List<PixelLog>> getPixelLogsBetweenDates(long unixStartDate, long unixEndDate);

    DataResult<List<PixelLog>> getPixelLogs();

    Result addPixelLogs(List<PixelLog> pixelLogs);

    Result addPixelLogsWithQuery(int startX, int endX, int startY, int endY, String ipAddress);


    DataResult<List<PixelLog>> getPixelLogsBySchoolMail(String schoolMail);
}
