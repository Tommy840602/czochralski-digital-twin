package com.twin.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Component
    @ConfigurationProperties(prefix = "app.register")
    public static class RegistrationProperties {
        /** 註冊是否要求手機 + 簡訊驗證碼 */
        private boolean smsRequired = true;

        public boolean isSmsRequired() {
            return smsRequired;
        }

        public void setSmsRequired(boolean smsRequired) {
            this.smsRequired = smsRequired;
        }
    }
}
