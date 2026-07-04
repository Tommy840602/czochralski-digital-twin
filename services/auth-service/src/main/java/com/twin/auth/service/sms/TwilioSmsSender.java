package com.twin.auth.service.sms;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 真正發簡訊。設定 app.sms.provider=twilio 時啟用，並需要：
 *   app.sms.twilio.account-sid / auth-token / from
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "twilio")
public class TwilioSmsSender implements SmsSender {

    private final String accountSid;
    private final String authToken;
    private final String from;

    public TwilioSmsSender(
            @Value("${app.sms.twilio.account-sid}") String accountSid,
            @Value("${app.sms.twilio.auth-token}") String authToken,
            @Value("${app.sms.twilio.from}") String from) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.from = from;
    }

    @PostConstruct
    void init() {
        Twilio.init(accountSid, authToken);
    }

    @Override
    public void send(String phone, String message) {
        Message.creator(new PhoneNumber(phone), new PhoneNumber(from), message).create();
    }
}
