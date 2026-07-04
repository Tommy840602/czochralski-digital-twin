package com.twin.furnace.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public HeaderAuthenticationFilter headerAuthenticationFilter() {
        return new HeaderAuthenticationFilter();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            HeaderAuthenticationFilter headerAuthenticationFilter
    ) throws Exception {

        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/ws/**").permitAll()

                        // 重點：下游 service URL 層先放行
                        // 真正 RBAC 交給 @PreAuthorize
                        .anyRequest().permitAll()
                )
                .addFilterBefore(
                        headerAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                res.sendError(HttpStatus.UNAUTHORIZED.value(), "未授權")
                        )
                        .accessDeniedHandler((req, res, e) ->
                                res.sendError(HttpStatus.FORBIDDEN.value(), "權限不足")
                        )
                )
                .build();
    }
}
