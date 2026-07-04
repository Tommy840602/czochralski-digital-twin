package com.twin.auth.service;

import com.twin.auth.exception.BadRequestException;
import com.twin.auth.exception.TooManyRequestsException;
import com.twin.auth.service.sms.SmsSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
public class SmsCodeService {

    private static final String CODE_KEY = "sms:code:";
    private static final String COOLDOWN_KEY = "sms:cooldown:";

    private final StringRedisTemplate redis;
    private final SmsSender sender;
    private final long codeTtlSec;
    private final long cooldownSec;
    private final SecureRandom random = new SecureRandom();

    public SmsCodeService(
            StringRedisTemplate redis,
            SmsSender sender,
            @Value("${app.sms.code-ttl-sec:300}") long codeTtlSec,
            @Value("${app.sms.cooldown-sec:60}") long cooldownSec) {
        this.redis = redis;
        this.sender = sender;
        this.codeTtlSec = codeTtlSec;
        this.cooldownSec = cooldownSec;
    }

    public void sendCode(String phone) {
        // 冷卻：同號碼 cooldownSec 內只能發一次
        Boolean firstTime = redis.opsForValue()
                .setIfAbsent(COOLDOWN_KEY + phone, "1", Duration.ofSeconds(cooldownSec));
        if (Boolean.FALSE.equals(firstTime)) {
            throw new TooManyRequestsException("發送過於頻繁，請稍後再試");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        redis.opsForValue().set(CODE_KEY + phone, code, Duration.ofSeconds(codeTtlSec));
        sender.send(phone, "您的驗證碼是 " + code + "（5 分鐘內有效）");
    }

    /** 驗證成功後立即刪除（一次性） */
    public void verify(String phone, String code) {
        String saved = redis.opsForValue().get(CODE_KEY + phone);
        if (saved == null) {
            throw new BadRequestException("驗證碼已過期，請重新發送");
        }
        if (!saved.equals(code)) {
            throw new BadRequestException("驗證碼錯誤");
        }
        redis.delete(CODE_KEY + phone);
    }
}
