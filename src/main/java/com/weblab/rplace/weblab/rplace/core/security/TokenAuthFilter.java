package com.weblab.rplace.weblab.rplace.core.security;

import com.weblab.rplace.weblab.rplace.business.abstracts.UserService;
import com.weblab.rplace.weblab.rplace.business.abstracts.UserTokenService;
import com.weblab.rplace.weblab.rplace.business.concretes.UserTokenManager;
import com.weblab.rplace.weblab.rplace.core.utilities.results.DataResult;
import com.weblab.rplace.weblab.rplace.dataAccess.abstracts.UserTokenDao;
import com.weblab.rplace.weblab.rplace.entities.User;
import com.weblab.rplace.weblab.rplace.entities.UserToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Stream;


@Component
public class TokenAuthFilter extends OncePerRequestFilter {

    private final UserTokenService userTokenService;

    private final UserService userService;

    @Autowired
    public TokenAuthFilter(UserTokenService userTokenService, UserService userService) {
        this.userTokenService = userTokenService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        Cookie[] cookies = request.getCookies();
        String token = null;

      if(cookies != null){
          for (Cookie cookie : cookies) {
              if (cookie.getName().equals("user_token")) {
                  token = cookie.getValue();
              }
              //System.out.println(cookies.length);
              //System.out.println(cookie.getName() + " " + cookie.getValue());
          }
      }

        String username = null;

        if(token != null){
            var result = userTokenService.getUserNameByToken(token);
            if (result.isSuccess()) {
                username = result.getData();
            }

            if(username != null && SecurityContextHolder.getContext().getAuthentication() == null){
                UserDetails user = userService.loadUserByUsername(username);
               UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
        }
    }

        filterChain.doFilter(request, response);
    }


}
