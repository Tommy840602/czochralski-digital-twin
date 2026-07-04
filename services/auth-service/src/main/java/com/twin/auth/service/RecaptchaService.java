package com.twin.auth.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.twin.auth.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class RecaptchaService {

    private static final Logger log = LoggerFactory.getLogger(RecaptchaService.class);

    private final RestTemplate http;
    private final String secret;
    private final String verifyUrl;

    public RecaptchaService(
            RestTemplate http,
            @Value("${app.recaptcha.secret:}") String secret,
            @Value("${app.recaptcha.verify-url:https://www.google.com/recaptcha/api/siteverify}") String verifyUrl) {
        this.http = http;
        this.secret = secret;
        this.verifyUrl = verifyUrl;
    }

    /** secret 沒設定時直接放行（方便本機開發）；設定了就嚴格驗。 */
    public void verify(String token, String remoteIp) {
        if (secret == null || secret.isBlank()) {
            log.warn("app.recaptcha.secret 未設定，略過 reCAPTCHA 驗證（dev 模式）");
            return;
        }
        if (token == null || token.isBlank()) {
            throw new BadRequestException("缺少 reCAPTCHA 驗證");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", token);
        if (remoteIp != null) form.add("remoteip", remoteIp);

        RecaptchaResponse resp;
        try {
            resp = http.postForObject(verifyUrl, form, RecaptchaResponse.class);
        } catch (Exception e) {
            log.error("呼叫 reCAPTCHA siteverify 失敗", e);
            throw new BadRequestException("reCAPTCHA 服務暫時無法使用");
        }

        if (resp == null || !resp.success()) {
            log.warn("reCAPTCHA 驗證未通過: {}", resp == null ? "null" : resp.errorCodes());
            throw new BadRequestException("reCAPTCHA 驗證失敗");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RecaptchaResponse(
            boolean success,
            String hostname,
            @JsonProperty("challenge_ts") String challengeTs,
            @JsonProperty("error-codes") List<String> errorCodes
    ) {}
}
