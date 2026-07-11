package com.twin.alarm.service;

import com.twin.alarm.slack.SlackNotifier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警中樞。依專案規範，Slack 通知「只在 AlarmService 裡觸發」，
 * 其他服務（如 SpcCheckService）呼叫本服務，而不直接呼叫 SlackNotifier。
 */
@Service
@RequiredArgsConstructor
public class AlarmService {

    private static final Logger log = LoggerFactory.getLogger(AlarmService.class);

    private final SlackNotifier slack;

    /** 每爐 Slack 節流秒數：多個參數同時 CRITICAL 時，避免短時間洗版。 */
    private static final long SLACK_THROTTLE_SECONDS = 120;
    private final Map<String, Instant> lastSlackByFurnace = new ConcurrentHashMap<>();

    /**
     * SPC CRITICAL 違規 → 發 Slack。
     * SpcCheckService 已對 furnace+param+rule 做 300s 去重，這裡再對「每爐」節流一次。
     */
    public void notifySpcCritical(String furnaceId, String ingotId, String paramName,
                                  int ruleId, String ruleName, double value,
                                  double mean, double ucl3, double lcl3) {
        Instant now = Instant.now();
        Instant last = lastSlackByFurnace.get(furnaceId);
        if (last != null && now.getEpochSecond() - last.getEpochSecond() < SLACK_THROTTLE_SECONDS) {
            return; // 節流：同一爐太近不重送
        }
        lastSlackByFurnace.put(furnaceId, now);

        String ingot = (ingotId == null || ingotId.isEmpty()) ? "—" : ingotId;
        String text = String.format(
                ":rotating_light: *SPC CRITICAL* — 長晶爐 %s（ingot %s）%n" +
                "參數 *%s* 觸發 Rule %d（%s）%n" +
                "量測值 `%.2f`，均值 `%.2f`，±3σ 管制界 `%.2f ~ %.2f`",
                furnaceId, ingot, paramName, ruleId, ruleName, value, mean, lcl3, ucl3);

        slack.send(text);
        log.info("AlarmService 觸發 Slack（CRITICAL）: furnace={} param={} rule={}",
                furnaceId, paramName, ruleId);
    }
}
