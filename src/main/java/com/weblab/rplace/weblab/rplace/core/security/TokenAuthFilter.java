package com.weblab.rplace.weblab.rplace.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weblab.rplace.weblab.rplace.business.abstracts.BanService;
import com.weblab.rplace.weblab.rplace.business.abstracts.UserService;
import com.weblab.rplace.weblab.rplace.business.abstracts.UserTokenService;
import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.core.utilities.results.ErrorResult;
import com.weblab.rplace.weblab.rplace.entities.UserToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Logs a request in with its user_token cookie. What the request may do comes from
 * the session alone (ADR 0060): ROLE_USER, plus ROLE_ADMIN or ROLE_MODERATOR on an
 * elevated e-skylab session. The user's rows in the authorities table grant nothing.
 * An elevated session past its end is deleted and the request is refused with 401.
 *
 * <p>Bans hold on every request (ticket 08): the session of a banned user is ended
 * (row and cookies) and the request goes on without it; a session used from a
 * banned address is left out of this request but kept. Endpoints that need a
 * session then answer 403 with the reason (BannedRequestEntryPoint); public ones
 * still answer. An address ban does not hold for an elevated session, so a
 * moderator on a banned shared network can still lift the ban.
 */
@Component
public class TokenAuthFilter extends OncePerRequestFilter {

    private final UserTokenService userTokenService;

    private final UserService userService;

    private final PlaceSessions placeSessions;

    private final Clock clock;

    private final ObjectMapper objectMapper;

    private final BanService banService;

    // The services stay lazy as before; a Clock cannot be proxied, so it is not.
    @Autowired
    public TokenAuthFilter(@Lazy UserTokenService userTokenService, @Lazy UserService userService, @Lazy PlaceSessions placeSessions,
                           Clock clock, ObjectMapper objectMapper, @Lazy BanService banService) {
        this.userTokenService = userTokenService;
        this.userService = userService;
        this.placeSessions = placeSessions;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.banService = banService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = PlaceSessions.sessionToken(request);
        UserToken session = token == null ? null : userTokenService.findSession(token);

        if (session != null && session.hasEndedBy(clock.instant())) {
            // Ended, not kept on as a user session: the next login reads the person's roles afresh.
            placeSessions.end(request, response);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(), new ErrorResult(Messages.sessionEnded));
            return;
        }

        if (session != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            var user = userService.getUserById(session.getUserId());
            if (user.isSuccess() && banService.isUserIdBanned(user.getData().getId())) {
                placeSessions.end(request, response);
                request.setAttribute(BannedRequestEntryPoint.BAN_MESSAGE, Messages.userIsBanned);
            } else if (user.isSuccess() && session.getRole() == null && banService.isIpAddressBanned(ClientIp.of(request))) {
                request.setAttribute(BannedRequestEntryPoint.BAN_MESSAGE, Messages.ipBanned);
            } else if (user.isSuccess()) {
                var authToken = new UsernamePasswordAuthenticationToken(user.getData(), token, session.grantedRoles());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }

}
