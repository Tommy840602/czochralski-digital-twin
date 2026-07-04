package com.twin.auth.controller;

import com.twin.auth.dto.PasswordResetDtos.ForgotPasswordRequest;
import com.twin.auth.dto.PasswordResetDtos.ForgotPasswordSmsRequest;
import com.twin.auth.dto.PasswordResetDtos.ResetPasswordRequest;
import com.twin.auth.dto.PasswordResetDtos.ResetPasswordSmsRequest;
import com.twin.auth.service.PasswordResetService;
import com.twin.auth.service.RecaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/password")
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordResetService svc;
    private final RecaptchaService recaptcha;

    /** email 申請重設：永遠回 200，不洩漏帳號是否存在 */
    @PostMapping("/forgot")
    public ResponseEntity<Void> forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        svc.requestByEmail(req.email());
        return ResponseEntity.ok().build();
    }

    /** 用 email 連結內的 token 重設 */
    @PostMapping("/reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest req) {
        svc.resetByToken(req.token(), req.newPassword());
        return ResponseEntity.ok().build();
    }

    /** 簡訊申請重設：reCAPTCHA 過了才發碼 */
    @PostMapping("/forgot-sms")
    public ResponseEntity<Void> forgotSms(@Valid @RequestBody ForgotPasswordSmsRequest req, HttpServletRequest http) {
        recaptcha.verify(req.recaptchaToken(), http.getRemoteAddr());
        svc.requestBySms(req.phone());
        return ResponseEntity.accepted().build();
    }

    /** 用簡訊碼重設 */
    @PostMapping("/reset-sms")
    public ResponseEntity<Void> resetSms(@Valid @RequestBody ResetPasswordSmsRequest req) {
        svc.resetBySms(req.phone(), req.smsCode(), req.newPassword());
        return ResponseEntity.ok().build();
    }
}
