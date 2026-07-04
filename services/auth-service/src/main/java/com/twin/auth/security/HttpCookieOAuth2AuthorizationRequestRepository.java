package com.twin.auth.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

import java.util.Base64;

/**
 * 把 OAuth2 授權請求(含 state) 存進 cookie，而非 server session。
 * 微服務 + 過 gateway 不保證 session 黏性，cookie 方案最穩。
 */
@Component
public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String COOKIE_NAME = "OAUTH2_AUTH_REQUEST";
    private static final int EXPIRE_SECONDS = 180;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return CookieUtils.get(request, COOKIE_NAME)
                .map(this::deserialize)
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            CookieUtils.delete(request, response, COOKIE_NAME);
            return;
        }
        String value = Base64.getUrlEncoder()
                .encodeToString(SerializationUtils.serialize(authorizationRequest));
        CookieUtils.add(response, COOKIE_NAME, value, EXPIRE_SECONDS);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        // cookie 在 success/failure handler 統一清除
        return loadAuthorizationRequest(request);
    }

    private OAuth2AuthorizationRequest deserialize(Cookie cookie) {
        return (OAuth2AuthorizationRequest) SerializationUtils.deserialize(
                Base64.getUrlDecoder().decode(cookie.getValue()));
    }
}
