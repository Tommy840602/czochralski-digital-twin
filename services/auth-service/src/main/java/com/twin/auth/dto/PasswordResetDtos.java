package com.twin.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PasswordResetDtos {

    public record ForgotPasswordRequest(
            @NotBlank @Email String email
    ) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72) String newPassword
    ) {}

    public record ForgotPasswordSmsRequest(
            @NotBlank @Size(max = 32) String phone,
            @NotBlank String recaptchaToken
    ) {}

    public record ResetPasswordSmsRequest(
            @NotBlank @Size(max = 32) String phone,
            @NotBlank String smsCode,
            @NotBlank @Size(min = 8, max = 72) String newPassword
    ) {}
}
