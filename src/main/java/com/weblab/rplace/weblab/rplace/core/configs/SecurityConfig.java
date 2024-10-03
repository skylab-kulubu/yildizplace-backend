package com.weblab.rplace.weblab.rplace.core.configs;

import com.weblab.rplace.weblab.rplace.business.abstracts.UserService;
import com.weblab.rplace.weblab.rplace.core.security.TokenAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig{

    private final TokenAuthFilter tokenAuthFilter;

    private final UserService userService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
       return http
               .csrf(AbstractHttpConfigurer::disable)
               .authorizeHttpRequests(x ->
                       x
                               .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                               .requestMatchers("/api/pixels/addPixel").hasAnyRole("ADMIN", "USER", "MODERATOR")
                               .requestMatchers("/api/pixels/getBoard").hasAnyRole("ADMIN", "MODERATOR")
                               .requestMatchers("/api/pixels/getColors").permitAll()
                               .requestMatchers("/api/pixels/getByXAndY").hasAnyRole("ADMIN", "MODERATOR")
                               .requestMatchers("/api/pixels/getPixelsBetweenDates").permitAll()
                               .requestMatchers("/api/pixels/fill").hasAnyRole("ADMIN", "MODERATOR")
                               .requestMatchers("/api/pixels/bringBackPixels").hasAnyRole("ADMIN", "MODERATOR")

                               .requestMatchers("/api/pixelLogs/**").hasAnyRole("ADMIN", "MODERATOR")

                               .requestMatchers("/api/users/register").permitAll()
                               .requestMatchers("/api/users/login").permitAll()
                               .requestMatchers("/api/users/logout").hasAnyRole("ADMIN","USER", "MODERATOR")
                               .requestMatchers("/api/users/addModerator").hasAnyRole("ADMIN")
                               .requestMatchers("/api/users/removeModerator").hasAnyRole("ADMIN")

                               .requestMatchers("/api/userTokens/**").hasAnyRole("ADMIN", "MODERATOR")

                               .requestMatchers("/api/bans/**").hasAnyRole("ADMIN", "MODERATOR")

                               .requestMatchers("/rplace/**").permitAll()

                               .requestMatchers("/api/whitelistedMails/**").permitAll()

                               .anyRequest().authenticated()

               )
               .sessionManagement(x -> x.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
               .authenticationProvider(authenticationProvider())
               .addFilterBefore(tokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
               .build();

    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setUserDetailsService(userService);
        return authenticationProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
