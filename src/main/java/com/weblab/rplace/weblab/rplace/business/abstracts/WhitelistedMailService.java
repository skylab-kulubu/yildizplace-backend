package com.weblab.rplace.weblab.rplace.business.abstracts;

import com.weblab.rplace.weblab.rplace.core.utilities.results.DataResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.entities.WhitelistedMail;

import java.util.List;

/**
 * The whitelist. Entries are revoked and restored, never deleted (ADR 0042); only
 * entries that are not revoked count. Nothing adds entries over HTTP (removed in
 * ticket 01 of ADR 0060), and no login reads the list today.
 */
public interface WhitelistedMailService {

    enum Lifecycle { CURRENT, REVOKED, ALL }

    enum Change { DONE, NOT_FOUND, CONFLICT }

    Result add(String mail);

    // Success while an entry for the address (any case) is not revoked.
    Result existsByMail(String mail);

    DataResult<List<WhitelistedMail>> list(Lifecycle lifecycle);

    // Idempotent; records when and by whom.
    Change revoke(int id);

    // Idempotent; CONFLICT while another entry for the same address counts.
    Change restore(int id);

}
