package com.twin.auth.controller;

import com.twin.auth.dto.AuthResponse;
import com.twin.auth.dto.LoginRequest;
import com.twin.auth.dto.RefreshRequest;
import com.twin.auth.dto.RegisterRequest;
import com.twin.auth.dto.SmsSendRequest;
import com.twin.auth.exception.BadRequestException;
import com.twin.auth.service.AuthService;
import com.twin.auth.service.RecaptchaService;
import com.twin.auth.service.SmsCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;
    private final RecaptchaService recaptcha;
    private final SmsCodeService smsCode;

    /** 註冊是否強制簡訊驗證。dev 預設關閉，方便測試 */
    @Value("${app.register.sms-required:false}")
    private boolean smsRequired;

    /** ④ reCAPTCHA 過了才發簡訊驗證碼 */
    @PostMapping("/sms/send")
    public ResponseEntity<Void> sendSms(@Valid @RequestBody SmsSendRequest req, HttpServletRequest http) {
        recaptcha.verify(req.recaptchaToken(), http.getRemoteAddr());
        smsCode.sendCode(req.phone());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        // ⚠ 這段原本「完全不存在」——smsCode 從頭到尾沒有被驗證過。
        //
        //   SmsCodeService.verify() 早就寫好了，但沒有任何人呼叫它。
        //   RegisterRequest 的註解還寫著「在 AuthService 裡視旗標檢查」，
        //   而 AuthService.register() 只檢查 username / email 是否重複。
        //   compose 傳的 REGISTER_SMS_REQUIRED=true 也沒有任何地方讀取。
        //
        //   結果：任何人直接 POST /auth/register，帶一個亂填的 smsCode
        //   （甚至完全不帶）都能註冊成功。整個簡訊驗證流程是裝飾用的。
        //   實測過：用隨機驗證碼 curl 一次就建出帳號。
        if (smsRequired) {
            if (req.phone() == null || req.phone().isBlank()) {
                throw new BadRequestException("請填寫手機號碼");
            }
            if (req.smsCode() == null || req.smsCode().isBlank()) {
                throw new BadRequestException("請填寫簡訊驗證碼");
            }
            smsCode.verify(req.phone(), req.smsCode());   // 驗證成功即刪除，一次性
        }
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
