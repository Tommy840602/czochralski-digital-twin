package com.twin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SmsSendRequest(
        @NotBlank @Size(max = 32) String phone,
        @NotBlank String recaptchaToken
) {}
