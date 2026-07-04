package com.twin.auth.service.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 預設 provider。不真的發簡訊，把內容印在 log，方便本機測試。
 * app.sms.provider=log（或不設）時啟用。
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "log", matchIfMissing = true)
public class LogSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LogSmsSender.class);

    @Override
    public void send(String phone, String message) {
        log.info("[SMS:log] to={} | {}", phone, message);
    }
}
