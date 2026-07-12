package com.twin.auth.config;

import com.twin.auth.security.HttpCookieOAuth2AuthorizationRequestRepository;
import com.twin.auth.security.OAuth2SuccessHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final HttpCookieOAuth2AuthorizationRequestRepository cookieAuthRequestRepo;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final String frontendLogin;

    /**
     * 第三方登入是「可選」的。
     *
     * Spring Boot 只有在 spring.security.oauth2.client.registration.* 有設定時，
     * 才會建立 ClientRegistrationRepository。沒設定就沒有這個 bean，
     * 這時若還無條件呼叫 .oauth2Login(...)，整個 SecurityFilterChain 會建不起來。
     *
     * 用 ObjectProvider 取代直接注入：拿得到就啟用 OAuth，拿不到就只跑帳密登入。
     * 要開啟第三方登入 → 啟用 `oauth` profile 並填好 client id/secret（見 application.yml）。
     */
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository;

    public SecurityConfig(HttpCookieOAuth2AuthorizationRequestRepository cookieAuthRequestRepo,
                          OAuth2SuccessHandler oAuth2SuccessHandler,
                          ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository,
                          @Value("${app.oauth.frontend-login:http://localhost:5173/login}") String frontendLogin) {
        this.cookieAuthRequestRepo = cookieAuthRequestRepo;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.clientRegistrationRepository = clientRegistrationRepository;
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
                        }));

        // 只有在真的有設定 OAuth client 時才掛上第三方登入
        if (clientRegistrationRepository.getIfAvailable() != null) {
            log.info("偵測到 OAuth2 client 設定 → 啟用第三方登入");
            http.oauth2Login(o -> o
                    .authorizationEndpoint(a -> a.authorizationRequestRepository(cookieAuthRequestRepo))
                    .successHandler(oAuth2SuccessHandler)
                    .failureHandler((request, response, ex) -> {
                        String target = UriComponentsBuilder.fromUriString(frontendLogin)
                                .queryParam("error", "oauth")
                                .queryParam("msg", ex.getMessage())
                                .build().toUriString();
                        response.sendRedirect(target);
                    }));
        } else {
            log.info("未設定 OAuth2 client → 只提供帳號密碼登入"
                    + "（要開啟第三方登入請啟用 `oauth` profile 並填 client id/secret）");
        }

        return http.build();
    }
}
