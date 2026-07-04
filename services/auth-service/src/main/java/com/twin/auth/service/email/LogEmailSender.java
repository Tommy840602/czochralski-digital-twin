package com.twin.auth.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 預設：不真寄信，把內容印 log，方便本機測試。app.email.provider=log（或不設）啟用。 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "log", matchIfMissing = true)
public class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[EMAIL:log] to={} | subject={}\n{}", to, subject, body);
    }
}
