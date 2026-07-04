package com.twin.auth.controller;

import com.twin.auth.dto.AuthResponse;
import com.twin.auth.dto.LoginRequest;
import com.twin.auth.dto.RefreshRequest;
import com.twin.auth.dto.RegisterRequest;
import com.twin.auth.dto.SmsSendRequest;
import com.twin.auth.service.AuthService;
import com.twin.auth.service.RecaptchaService;
import com.twin.auth.service.SmsCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;
    private final RecaptchaService recaptcha;
    private final SmsCodeService smsCode;

    /** ④ reCAPTCHA 過了才發簡訊驗證碼 */
    @PostMapping("/sms/send")
    public ResponseEntity<Void> sendSms(@Valid @RequestBody SmsSendRequest req, HttpServletRequest http) {
        recaptcha.verify(req.recaptchaToken(), http.getRemoteAddr());
        smsCode.sendCode(req.phone());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return auth.refresh(req.refreshToken());
    }
}
