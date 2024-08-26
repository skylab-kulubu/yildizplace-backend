package com.weblab.rplace.weblab.rplace.business.concretes;

import com.weblab.rplace.weblab.rplace.business.abstracts.PixelLogService;
import com.weblab.rplace.weblab.rplace.business.abstracts.PixelService;
import com.weblab.rplace.weblab.rplace.business.abstracts.UserService;
import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.core.utilities.results.*;
import com.weblab.rplace.weblab.rplace.dataAccess.abstracts.PixelLogDao;
import com.weblab.rplace.weblab.rplace.entities.Pixel;
import com.weblab.rplace.weblab.rplace.entities.PixelLog;
import com.weblab.rplace.weblab.rplace.entities.User;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class PixelLogManager implements PixelLogService {

    private final PixelLogDao pixelLogDao;

    private final PixelService pixelService;

    private final EntityManager entityManager;

    private final UserService userService;

    public PixelLogManager(EntityManager entityManager, PixelLogDao pixelLogDao, @Lazy PixelService pixelService, UserService userService) {
        this.entityManager = entityManager;
        this.pixelLogDao = pixelLogDao;
        this.pixelService = pixelService;
        this.userService = userService;
    }

    @Override
    public Result addPixelLog(Pixel pixel,String oldColor, String placerIp) {
       var loggedInUserResult = userService.getAuthenticatedUser();
        if (!loggedInUserResult.isSuccess()){
            return loggedInUserResult;
        }

        var loggedInUser = loggedInUserResult.getData();

        loggedInUser.setLastPlacedAt(new Date());

        PixelLog pixelLog = PixelLog.builder()
                .pixel(pixel)
                //.oldColor(oldColor)
                .newColor(pixel.getColor())
                .placerIp(placerIp)
                .placedAt(new Date())
                .user(loggedInUser)
                .build();

        pixelLogDao.save(pixelLog);

        return new SuccessResult(Messages.pixelLogAdded);
    }

    @Override
    public DataResult<List<PixelLog>> getPixelLogsByPixelId(int pixelId) {
        var result = pixelLogDao.findAllByPixelIdOrderByPlacedAtDesc(pixelId);
            if(result == null){
                return new ErrorDataResult<>(Messages.getPixelsUnsuccessful);
            }


        return new SuccessDataResult<List<PixelLog>>(result, Messages.pixelLogsListed);

    }

    @Override
    public DataResult<List<PixelLog>> getPixelLogsByPlacerIp(String placerIp) {
        var result = pixelLogDao.findAllByPlacerIpOrderByPlacedAtDesc(placerIp);

        if(result == null){
            return new ErrorDataResult<>(Messages.getPixelsUnsuccessful);
        }

        return new SuccessDataResult<List<PixelLog>>(result, Messages.pixelLogsListed);
    }

    @Override
    public DataResult<List<PixelLog>> getPixelLogsByXAndY(int x, int y) {
        var result = pixelService.getByXAndY(x, y);
        if (!result.isSuccess()) {
            return new ErrorDataResult<>(result.getMessage());
        }
        int pixelId = result.getData().getId();

        return getPixelLogsByPixelId(pixelId);

    }

    @Override
    public DataResult<List<PixelLog>> getPixelLogsBetweenDates(long unixStartDate, long unixEndDate) {
        Date startDate = new Date(unixStartDate * 1000);
        Date endDate = new Date(unixEndDate * 1000);

        var result = pixelLogDao.findAllByPlacedAtBetweenOrderByPlacedAt(startDate, endDate);
        if(result == null){
            return new ErrorDataResult<>(Messages.getPixelsUnsuccessful);
        }
        if (result.isEmpty()){
            return new ErrorDataResult<>(Messages.noPixelsFound);
        }


        return new SuccessDataResult<List<PixelLog>>(result, Messages.pixelLogsListed);
    }

    @Override
    public DataResult<List<PixelLog>> getPixelLogs() {
        var result = pixelLogDao.findAll();
        if(result == null){
            return new ErrorDataResult<>(Messages.getPixelsUnsuccessful);
        }
        return new SuccessDataResult<List<PixelLog>>(result, Messages.pixelLogsListed);
    }

    @Override
    public Result addPixelLogs(List<PixelLog> pixelLogs) {
        pixelLogDao.saveAll(pixelLogs);
        return new SuccessResult(Messages.pixelLogsAdded);
    }


    public Result addPixelLogsWithQuery(int startX, int endX, int startY, int endY, String ipAddress){
        var loggedInUserResult = userService.getAuthenticatedUser();
        if (!loggedInUserResult.isSuccess()){
            return loggedInUserResult;
        }

        User loggedInUser = loggedInUserResult.getData();

            entityManager.createNativeQuery("INSERT INTO pixel_logs (new_color, placed_at, placer_ip, pixel_id, user_id) SELECT color, CURRENT_TIMESTAMP, ?, id, ? FROM pixels WHERE x BETWEEN ? AND ? AND y BETWEEN ? AND ?;")
                    .setParameter(1, ipAddress)
                    .setParameter(2, loggedInUser.getId())
                    .setParameter(3, startX)
                    .setParameter(4, endX)
                    .setParameter(5, startY)
                    .setParameter(6, endY)
                    .executeUpdate();

            return new SuccessResult(Messages.pixelLogsAdded);
    }

    @Override
    public DataResult<List<PixelLog>> getPixelLogsBySchoolMail(String schoolMail) {

        var result = pixelLogDao.findAllByUserSchoolMail(schoolMail);
        if(result == null){
            return new ErrorDataResult<>(Messages.getPixelsUnsuccessful);
        }
        return new SuccessDataResult<List<PixelLog>>(result, Messages.pixelLogsListed);
    }


}
