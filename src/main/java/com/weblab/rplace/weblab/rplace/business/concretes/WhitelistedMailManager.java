package com.weblab.rplace.weblab.rplace.business.concretes;

import com.weblab.rplace.weblab.rplace.business.abstracts.UserService;
import com.weblab.rplace.weblab.rplace.business.abstracts.WhitelistedMailService;
import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.core.utilities.results.DataResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.ErrorResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.core.utilities.results.SuccessDataResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.SuccessResult;
import com.weblab.rplace.weblab.rplace.dataAccess.abstracts.WhitelistedMailDao;
import com.weblab.rplace.weblab.rplace.entities.ModerationAuditEntry;
import com.weblab.rplace.weblab.rplace.entities.WhitelistedMail;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class WhitelistedMailManager implements WhitelistedMailService {

    private final WhitelistedMailDao whitelistedMailDao;

    private final UserService userService;

    private final ModerationAudit audit;

    private final Clock clock;

    public WhitelistedMailManager(WhitelistedMailDao whitelistedMailDao, @Lazy UserService userService, ModerationAudit audit, Clock clock) {
        this.whitelistedMailDao = whitelistedMailDao;
        this.userService = userService;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public Result add(String mail) {

        if(whitelistedMailDao.existsByMailIgnoreCase(mail)){
            return new ErrorResult(Messages.whitelistedMailAlreadyExists);
        }

        var whiteListedMailToAdd = WhitelistedMail.builder()
                        .mail(mail).build();

        whitelistedMailDao.save(whiteListedMailToAdd);

        return new SuccessResult(Messages.whitelistedMailAdded);


    }

    @Override
    public Result existsByMail(String mail) {
        boolean result = whitelistedMailDao.existsByMailIgnoreCaseAndRevokedAtIsNull(mail);

        if(result){
            return new SuccessResult(Messages.whitelistedMailExists);
        }

        return new ErrorResult(Messages.whitelistedMailDoesNotExist);
    }

    @Override
    public DataResult<List<WhitelistedMail>> list(Lifecycle lifecycle) {
        List<WhitelistedMail> entries = switch (lifecycle) {
            case CURRENT -> whitelistedMailDao.findAllByRevokedAtIsNullOrderByIdAsc();
            case REVOKED -> whitelistedMailDao.findAllByRevokedAtIsNotNullOrderByIdAsc();
            case ALL -> whitelistedMailDao.findAllByOrderByIdAsc();
        };
        return new SuccessDataResult<>(entries, Messages.whitelistedMailsListed);
    }

    @Override
    @Transactional
    public Change revoke(int id) {
        WhitelistedMail entry = whitelistedMailDao.findById(id).orElse(null);
        if (entry == null) {
            return Change.NOT_FOUND;
        }
        if (entry.getRevokedAt() == null) {
            var actor = userService.getAuthenticatedUser();
            entry.setRevokedAt(clock.instant());
            entry.setRevokedById(actor.isSuccess() ? actor.getData().getId() : null);
            whitelistedMailDao.save(entry);
            audit.record(ModerationAuditEntry.REVOKE_WHITELISTED_MAIL, entry.getMail(), null);
        }
        return Change.DONE;
    }

    @Override
    @Transactional
    public Change restore(int id) {
        WhitelistedMail entry = whitelistedMailDao.findById(id).orElse(null);
        if (entry == null) {
            return Change.NOT_FOUND;
        }
        if (entry.getRevokedAt() == null) {
            return Change.DONE;
        }
        if (whitelistedMailDao.existsByMailIgnoreCaseAndRevokedAtIsNullAndIdNot(entry.getMail(), id)) {
            return Change.CONFLICT;
        }
        entry.setRevokedAt(null);
        entry.setRevokedById(null);
        whitelistedMailDao.save(entry);
        audit.record(ModerationAuditEntry.RESTORE_WHITELISTED_MAIL, entry.getMail(), null);
        return Change.DONE;
    }
}
