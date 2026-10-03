package com.weblab.rplace.weblab.rplace.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weblab.rplace.weblab.rplace.core.utilities.results.ErrorResult;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * What a request without a usable session gets from an endpoint that needs one: 403,
 * as before. When TokenAuthFilter left the session out because of a ban, the body
 * also says so ({"success":false,"message":"Bu kullanıcı yasaklı!"} or the IP
 * message), the way the API reports every other refusal.
 */
@Component
public class BannedRequestEntryPoint implements AuthenticationEntryPoint {

    static final String BAN_MESSAGE = BannedRequestEntryPoint.class.getName() + ".banMessage";

    private final AuthenticationEntryPoint otherwise = new Http403ForbiddenEntryPoint();

    private final ObjectMapper objectMapper;

    public BannedRequestEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        Object banMessage = request.getAttribute(BAN_MESSAGE);
        if (banMessage == null) {
            otherwise.commence(request, response, authException);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), new ErrorResult(banMessage.toString()));
    }
}
