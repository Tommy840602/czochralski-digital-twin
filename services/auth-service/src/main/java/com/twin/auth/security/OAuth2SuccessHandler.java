package com.twin.auth.security;

import com.twin.auth.entity.Permission;
import com.twin.auth.entity.Role;
import com.twin.auth.entity.User;
import com.twin.auth.service.OAuth2ProvisioningService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuth2ProvisioningService provisioning;
    private final JwtService jwt;
    private final String frontendBase;

    public OAuth2SuccessHandler(OAuth2ProvisioningService provisioning,
                                JwtService jwt,
                                @Value("${app.oauth.frontend-callback:http://localhost:5173/login/callback}") String frontendBase) {
        this.provisioning = provisioning;
        this.jwt = jwt;
        this.frontendBase = frontendBase;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String registrationId = token.getAuthorizedClientRegistrationId();
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();

        User user = provisioning.upsert(registrationId, principal);

        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName).collect(Collectors.toSet());
        Set<String> perms = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getName).collect(Collectors.toSet());

        String access = jwt.generateAccess(user, roleNames, perms);
        String refresh = jwt.generateRefresh(user);

        CookieUtils.delete(request, response, HttpCookieOAuth2AuthorizationRequestRepository.COOKIE_NAME);

        String target = UriComponentsBuilder.fromUriString(frontendBase)
                .queryParam("accessToken", access)
                .queryParam("refreshToken", refresh)
                .queryParam("username", user.getUsername())
                .queryParam("roles", String.join(",", roleNames))
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, target);
    }
}

