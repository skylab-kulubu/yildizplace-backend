package com.weblab.rplace.weblab.rplace.webAPI.controllers;

import com.weblab.rplace.weblab.rplace.business.abstracts.WhitelistedMailService;
import com.weblab.rplace.weblab.rplace.business.abstracts.WhitelistedMailService.Change;
import com.weblab.rplace.weblab.rplace.business.abstracts.WhitelistedMailService.Lifecycle;
import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.core.utilities.results.ErrorResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.core.utilities.results.SuccessResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

/**
 * Moderators and admins manage the whitelist (SecurityConfig): list it, revoke an
 * entry, restore one (ADR 0042: revoked, never deleted). There is no add.
 */
@RestController
@RequestMapping("api/whitelistedMails")
@RequiredArgsConstructor
public class WhitelistedMailController {

    private final WhitelistedMailService whitelistedMailService;

    // lifecycle: current (default), revoked or all.
    @GetMapping
    public ResponseEntity<? extends Result> list(@RequestParam(defaultValue = "current") String lifecycle) {
        Lifecycle filter;
        try {
            filter = Lifecycle.valueOf(lifecycle.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResult(Messages.whitelistLifecycleInvalid));
        }
        return ResponseEntity.ok(whitelistedMailService.list(filter));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<Result> revoke(@PathVariable int id) {
        return answer(whitelistedMailService.revoke(id), Messages.whitelistedMailRevoked);
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Result> restore(@PathVariable int id) {
        return answer(whitelistedMailService.restore(id), Messages.whitelistedMailRestored);
    }

    private static ResponseEntity<Result> answer(Change change, String done) {
        return switch (change) {
            case DONE -> ResponseEntity.ok(new SuccessResult(done));
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResult(Messages.whitelistedMailDoesNotExist));
            case CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResult(Messages.whitelistedMailRestoreConflict));
        };
    }
}
