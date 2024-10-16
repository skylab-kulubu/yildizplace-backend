package com.weblab.rplace.weblab.rplace.business.abstracts;


import com.weblab.rplace.weblab.rplace.core.utilities.results.DataResult;
import com.weblab.rplace.weblab.rplace.entities.FinalPixel;

public interface FinalPixelService {

    DataResult<FinalPixel> getFinalPixelByXAndY(int x, int y);


}
