package com.weblab.rplace.weblab.rplace.business.concretes;

import com.weblab.rplace.weblab.rplace.dataAccess.abstracts.ModerationAuditDao;
import com.weblab.rplace.weblab.rplace.entities.ModerationAuditEntry;
import com.weblab.rplace.weblab.rplace.entities.Role;
import com.weblab.rplace.weblab.rplace.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Writes the moderation audit log (moderation_audit_log) for the user of the current request. */
@Component
public class ModerationAudit {

    private static final Logger log = LoggerFactory.getLogger(ModerationAudit.class);

    private final ModerationAuditDao dao;

    private final Clock clock;

    public ModerationAudit(ModerationAuditDao dao, Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }

    public void record(String action, String target, String reason) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Integer actorId = auth != null && auth.getPrincipal() instanceof User user ? user.getId() : null;
        String actorRole = auth == null ? null : highestRole(auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet()));

        dao.save(ModerationAuditEntry.builder()
                .action(action)
                .target(target)
                .reason(reason)
                .actorUserId(actorId)
                .actorRole(actorRole)
                .at(clock.instant())
                .build());
        // Who and what, never the target: it is an address or a person's school mail.
        log.info("moderation: {} by user {} ({})", action, actorId, actorRole);
    }

    public List<ModerationAuditEntry> newestFirst() {
        return dao.findAllByOrderByIdDesc();
    }

    private static String highestRole(Set<String> authorities) {
        for (Role role : List.of(Role.ROLE_ADMIN, Role.ROLE_MODERATOR, Role.ROLE_USER)) {
            if (authorities.contains(role.getAuthority())) {
                return role.getAuthority();
            }
        }
        return null;
    }
}
