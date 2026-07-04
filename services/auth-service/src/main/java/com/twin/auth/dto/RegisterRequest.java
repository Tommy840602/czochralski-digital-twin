package com.twin.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * phone / smsCode 是否必填由 app.register.sms-required 控制，
 * 在 AuthService 裡視旗標檢查（這樣關掉 SMS 時也能註冊，方便測試）。
 */
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Email @Size(max = 128) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 32) String phone,
        String smsCode
) {}
