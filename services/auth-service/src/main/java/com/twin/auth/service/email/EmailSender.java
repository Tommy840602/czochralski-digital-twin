package com.twin.auth.service.email;

public interface EmailSender {
    void send(String to, String subject, String body);
}
