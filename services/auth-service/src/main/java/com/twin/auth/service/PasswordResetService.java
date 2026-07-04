package com.twin.auth.service;

import com.twin.auth.entity.User;
import com.twin.auth.exception.BadRequestException;
import com.twin.auth.repository.UserRepository;
import com.twin.auth.service.email.EmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String KEY = "pwdreset:";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final StringRedisTemplate redis;
    private final EmailSender email;
    private final SmsCodeService smsCode;

    @Value("${app.password-reset.ttl-sec:1800}")
    private long ttlSec;

    @Value("${app.password-reset.frontend-base:http://localhost:5173/reset-password}")
    private String frontendBase;

    // ── email 流程 ──────────────────────────────────────────────

    /** 不論帳號是否存在都不丟例外（外層一律回 200，避免帳號列舉）。 */
    public void requestByEmail(String emailAddr) {
        users.findByEmail(emailAddr)
                .filter(u -> "LOCAL".equals(u.getProvider())) // 第三方帳號沒本地密碼可改
                .ifPresent(u -> {
                    String token = UUID.randomUUID().toString().replace("-", "");
                    redis.opsForValue().set(KEY + token, String.valueOf(u.getId()), Duration.ofSeconds(ttlSec));
                    String link = frontendBase + "?token=" + token;
                    email.send(u.getEmail(), "重設密碼",
                            "您申請了重設密碼。請點擊以下連結（30 分鐘內有效）：\n" + link
                                    + "\n\n若非本人操作，請忽略此信。");
                });
    }

    @Transactional
    public void resetByToken(String token, String newPassword) {
        String key = KEY + token;
        String uid = redis.opsForValue().get(key);
        if (uid == null) {
            throw new BadRequestException("連結已失效或不存在，請重新申請");
        }
        User u = users.findById(Long.valueOf(uid))
                .orElseThrow(() -> new BadRequestException("使用者不存在"));
        u.setPasswordHash(encoder.encode(newPassword));
        users.save(u);
        redis.delete(key); // 一次性
    }

    // ── 簡訊流程 ────────────────────────────────────────────────

    /** reCAPTCHA 由 controller 先擋；這裡沿用 SmsCodeService 的冷卻 + TTL。 */
    public void requestBySms(String phone) {
        smsCode.sendCode(phone);
    }

    @Transactional
    public void resetBySms(String phone, String code, String newPassword) {
        smsCode.verify(phone, code); // 失敗丟 BadRequestException
        User u = users.findFirstByPhone(phone)
                .filter(x -> "LOCAL".equals(x.getProvider()))
                .orElseThrow(() -> new BadRequestException("查無此手機對應的本地帳號"));
        u.setPasswordHash(encoder.encode(newPassword));
        users.save(u);
    }
}
