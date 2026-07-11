package com.twin.alarm.slack;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Slack Incoming Webhook 送信器（低階）。
 * 只負責把文字 POST 到 webhook；是否觸發由 AlarmService 決定。
 * webhook 未設定或仍為佔位值時自動停用，不影響主流程。
 */
@Component
public class SlackNotifier {

    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);

    private final String webhookUrl;
    private final boolean enabled;
    private final RestTemplate rest = new RestTemplate();

    public SlackNotifier(@Value("${slack.webhook.url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.enabled = !this.webhookUrl.isEmpty()
                && this.webhookUrl.startsWith("https://hooks.slack.com/")
                && !this.webhookUrl.toUpperCase().contains("YOUR");
    }

    @PostConstruct
    void logStatus() {
        if (enabled) {
            log.info("SlackNotifier 已啟用（webhook 已設定）");
        } else {
            log.warn("SlackNotifier 未啟用：slack.webhook.url 未設定或仍為佔位值，Slack 通知不會送出");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 送一則純文字（支援 Slack mrkdwn）訊息；未啟用或失敗都只記 log，不拋出。 */
    public void send(String text) {
        if (!enabled) {
            log.debug("Slack 未啟用，略過通知: {}", text);
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("text", text), headers);
            rest.postForEntity(webhookUrl, entity, String.class);
        } catch (Exception e) {
            log.error("送 Slack 失敗: {}", e.getMessage());
        }
    }
}
