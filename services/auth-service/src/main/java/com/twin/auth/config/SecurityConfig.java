package com.twin.auth.config;

import com.twin.auth.security.HttpCookieOAuth2AuthorizationRequestRepository;
import com.twin.auth.security.OAuth2SuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final HttpCookieOAuth2AuthorizationRequestRepository cookieAuthRequestRepo;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final String frontendLogin;

    public SecurityConfig(HttpCookieOAuth2AuthorizationRequestRepository cookieAuthRequestRepo,
                          OAuth2SuccessHandler oAuth2SuccessHandler,
                          @Value("${app.oauth.frontend-login:http://localhost:5173/login}") String frontendLogin) {
        this.cookieAuthRequestRepo = cookieAuthRequestRepo;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.frontendLogin = frontendLogin;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/oauth2/**", "/login/**", "/actuator/**", "/error").permitAll()
                        .anyRequest().authenticated())
                // 認證失敗回 401 JSON、不做 302 導向（避免 container hostname redirect）
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((req, resp, e) -> {
                            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            resp.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"" + e.getMessage() + "\"}");
                        }))
                .oauth2Login(o -> o
                        .authorizationEndpoint(a -> a.authorizationRequestRepository(cookieAuthRequestRepo))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler((request, response, ex) -> {
                            String target = UriComponentsBuilder.fromUriString(frontendLogin)
                                    .queryParam("error", "oauth")
                                    .queryParam("msg", ex.getMessage())
                                    .build().toUriString();
                            response.sendRedirect(target);
                        }));
        return http.build();
    }
}
