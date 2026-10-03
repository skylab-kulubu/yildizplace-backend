package com.weblab.rplace.weblab.rplace.core.security;

import com.weblab.rplace.weblab.rplace.dataAccess.abstracts.UserTokenDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Moves login link and session values that versions before hashing stored in the
 * clear to their SHA-256 (ticket 08). Runs on every start, after Hibernate has added
 * token_hash and before the server takes requests, so the values in the mails and
 * cookies people already have keep working: nobody is logged out and no mailed link
 * stops working. Idempotent; a start with nothing in the clear changes nothing.
 */
@Component
public class UserTokenHashing implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(UserTokenHashing.class);

    private final UserTokenDao userTokenDao;

    public UserTokenHashing(UserTokenDao userTokenDao) {
        this.userTokenDao = userTokenDao;
    }

    @Override
    public void afterSingletonsInstantiated() {
        hashValuesStoredInTheClear();
    }

    /** Returns how many rows it hashed. */
    public int hashValuesStoredInTheClear() {
        int hashed = userTokenDao.hashAllStoredInTheClear();
        if (hashed > 0) {
            log.info("user_tokens: hashed {} login link or session values stored in the clear", hashed);
        }
        return hashed;
    }
}
