package com.twin.auth.service.sms;

public interface SmsSender {
    void send(String phone, String message);
}
