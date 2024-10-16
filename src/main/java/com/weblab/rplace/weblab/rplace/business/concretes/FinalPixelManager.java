package com.weblab.rplace.weblab.rplace.business.concretes;

import com.weblab.rplace.weblab.rplace.business.abstracts.FinalPixelService;
import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.core.utilities.results.DataResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.ErrorDataResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.SuccessDataResult;
import com.weblab.rplace.weblab.rplace.dataAccess.abstracts.FinalPixelDao;
import com.weblab.rplace.weblab.rplace.entities.FinalPixel;
import org.springframework.stereotype.Service;

@Service
public class FinalPixelManager implements FinalPixelService {

    private final FinalPixelDao finalPixelDao;

    public FinalPixelManager(FinalPixelDao finalPixelDao) {
        this.finalPixelDao = finalPixelDao;
    }

    @Override
    public DataResult<FinalPixel> getFinalPixelByXAndY(int x, int y) {
        var finalPixel = finalPixelDao.findByXAndY(x, y);
        if (finalPixel.isEmpty()) {
            return new ErrorDataResult<>(null, Messages.finalPixelNotFound);
        }
        return new SuccessDataResult<>(finalPixel.get(), Messages.finalPixelRetrieved);
    }
}
