package com.twin.auth.service;

import com.twin.auth.entity.Role;
import com.twin.auth.entity.User;
import com.twin.auth.repository.RoleRepository;
import com.twin.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OAuth2ProvisioningService {

    private static final String DEFAULT_ROLE = "VIEWER";

    private final UserRepository users;
    private final RoleRepository roles;

    /**
     * registrationId: github / google / azure
     * 依 provider 抽出 id / email / name，找不到就建新帳號（VIEWER）。
     */
    @Transactional
    public User upsert(String registrationId, OAuth2User principal) {
        Map<String, Object> a = principal.getAttributes();
        String provider = registrationId.toUpperCase();

        String providerId;
        String email;
        String displayName;

        switch (registrationId) {
            case "github" -> {
                providerId = String.valueOf(a.get("id"));
                String login = (String) a.get("login");
                Object e = a.get("email");
                email = e != null ? e.toString() : login + "@users.noreply.github.com";
                displayName = login;
            }
            case "google" -> {
                providerId = String.valueOf(a.get("sub"));
                email = (String) a.get("email");
                displayName = (String) a.getOrDefault("name", email);
            }
            case "azure" -> {
                providerId = String.valueOf(a.get("sub"));
                Object e = a.get("email");
                if (e == null) e = a.get("preferred_username");
                if (e == null) e = a.get("upn");
                email = e != null ? e.toString() : providerId + "@azure.local";
                displayName = (String) a.getOrDefault("name", email);
            }
            default -> throw new IllegalArgumentException("不支援的 provider: " + registrationId);
        }

        // 已存在 → 直接回
        User existing = users.findByProviderAndProviderId(provider, providerId).orElse(null);
        if (existing != null) return existing;

        Role viewer = roles.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("預設角色不存在，請確認 seed"));

        User u = new User();
        u.setUsername(uniqueUsername(displayName, email));
        u.setEmail(uniqueEmail(email, provider, providerId));
        u.setPasswordHash(null); // 第三方帳號沒有本地密碼
        u.setProvider(provider);
        u.setProviderId(providerId);
        u.setEnabled(true);
        u.getRoles().add(viewer);
        return users.save(u);
    }

    private String uniqueUsername(String base, String email) {
        String candidate = base != null && !base.isBlank()
                ? base.replaceAll("\\s+", "_")
                : email.split("@")[0];
        String name = candidate;
        int i = 1;
        while (users.existsByUsername(name)) {
            name = candidate + "_" + (i++);
        }
        return name;
    }

    private String uniqueEmail(String email, String provider, String providerId) {
        // email 可能跟既有本地帳號撞；撞了就用 provider 前綴避免 unique 衝突
        if (email == null || users.existsByEmail(email)) {
            return provider.toLowerCase() + "_" + providerId + "@oauth.local";
        }
        return email;
    }
}
