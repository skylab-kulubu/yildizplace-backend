package com.weblab.rplace.weblab.rplace.business.abstracts;

import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;

public interface WhitelistedMailService {

    Result add(String mail);

    Result existsByMail(String mail);

    Result deleteByMail(String mail);


}
