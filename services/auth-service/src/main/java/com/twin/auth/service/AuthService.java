package com.twin.auth.service;

import com.twin.auth.dto.AuthResponse;
import com.twin.auth.dto.LoginRequest;
import com.twin.auth.dto.RegisterRequest;
import com.twin.auth.entity.Permission;
import com.twin.auth.entity.Role;
import com.twin.auth.entity.User;
import com.twin.auth.exception.ConflictException;
import com.twin.auth.exception.UnauthorizedException;
import com.twin.auth.repository.RoleRepository;
import com.twin.auth.repository.UserRepository;
import com.twin.auth.security.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DEFAULT_ROLE = "VIEWER";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByUsername(req.username())) throw new ConflictException("username 已被使用");
        if (users.existsByEmail(req.email())) throw new ConflictException("email 已被使用");

        Role viewer = roles.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("預設角色 " + DEFAULT_ROLE + " 不存在，請確認 seed 已執行"));

        User u = new User();
        u.setUsername(req.username());
        u.setEmail(req.email());
        u.setPhone(req.phone());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setProvider("LOCAL");
        u.getRoles().add(viewer);
        users.save(u);

        return issue(u);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User u = users.findByUsernameOrEmail(req.usernameOrEmail(), req.usernameOrEmail())
                .orElseThrow(() -> new UnauthorizedException("帳號或密碼錯誤"));
        if (!u.isEnabled()) throw new UnauthorizedException("帳號已停用");
        if (u.getPasswordHash() == null || !encoder.matches(req.password(), u.getPasswordHash())) {
            throw new UnauthorizedException("帳號或密碼錯誤");
        }
        return issue(u);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        Claims c;
        try {
            c = jwt.parse(refreshToken).getPayload();
        } catch (Exception e) {
            throw new UnauthorizedException("refresh token 無效或已過期");
        }
        if (!"refresh".equals(c.get("typ", String.class))) {
            throw new UnauthorizedException("token 類型錯誤");
        }
        User u = users.findByUsername(c.getSubject())
                .orElseThrow(() -> new UnauthorizedException("使用者不存在"));
        return issue(u);
    }

    private AuthResponse issue(User u) {
        Set<String> roleNames = u.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        Set<String> perms = u.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getName)
                .collect(Collectors.toSet());
        String access = jwt.generateAccess(u, roleNames, perms);
        String refresh = jwt.generateRefresh(u);
        return AuthResponse.of(access, refresh, u.getUsername(), roleNames);
    }
}
