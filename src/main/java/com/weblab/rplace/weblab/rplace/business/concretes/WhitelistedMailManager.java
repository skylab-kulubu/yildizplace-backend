package com.weblab.rplace.weblab.rplace.business.concretes;

import com.weblab.rplace.weblab.rplace.business.abstracts.WhitelistedMailService;
import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.core.utilities.results.ErrorResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.core.utilities.results.SuccessResult;
import com.weblab.rplace.weblab.rplace.dataAccess.abstracts.WhitelistedMailDao;
import com.weblab.rplace.weblab.rplace.entities.WhitelistedMail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WhitelistedMailManager implements WhitelistedMailService {

    @Autowired
    private WhitelistedMailDao whitelistedMailDao;

    @Override
    public Result add(String mail) {

        if(whitelistedMailDao.existsByMail(mail)){
            return new ErrorResult(Messages.whitelistedMailAlreadyExists);
        }

        var whiteListedMailToAdd = WhitelistedMail.builder()
                        .mail(mail).build();

        whitelistedMailDao.save(whiteListedMailToAdd);

        return new SuccessResult(Messages.whitelistedMailAdded);


    }

    @Override
    public Result existsByMail(String mail) {
        boolean result = whitelistedMailDao.existsByMail(mail);

        if(result){
            return new SuccessResult(Messages.whitelistedMailExists);
        }

        return new ErrorResult(Messages.whitelistedMailDoesNotExist);
    }

    @Override
    public Result deleteByMail(String mail) {
        var whiteListedMailToDelete = whitelistedMailDao.findByMail(mail);

        if(whiteListedMailToDelete == null){
            return new ErrorResult(Messages.whitelistedMailDoesNotExist);
        }

        whitelistedMailDao.delete(whiteListedMailToDelete);

        return new SuccessResult(Messages.whitelistedMailDeleted);
    }
}
