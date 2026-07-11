package com.twin.alarm.service;

import com.twin.alarm.entity.SpcBaseline;
import com.twin.alarm.entity.SpcViolation;
import com.twin.alarm.repository.SpcBaselineRepository;
import com.twin.alarm.repository.SpcViolationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class SpcCheckService {

    private static final Logger log = LoggerFactory.getLogger(SpcCheckService.class);

    private final SpcRuleEngine ruleEngine;
    private final SpcBaselineRepository baselineRepo;
    private final SpcViolationRepository violationRepo;
    private final KafkaTemplate<String, String> kafka;
    private final AlarmService alarmService;

    public static final String SPC_ALERTS_TOPIC = "spc-alerts";

    /** 同一 furnace+param+rule 在 DEDUPE_SECONDS 秒內只寫一次、避免 DB 爆炸 */
    private static final long DEDUPE_SECONDS = 300;
    private final Map<String, Instant> lastRecordTime = new ConcurrentHashMap<>();

    /** 判定爐子「運轉中」的加熱器溫度門檻（與 OeeService 的 availability 判準一致） */
    private static final double RUNNING_HEATER_TEMP_C = 20.0;

    public void checkAllParams(String furnaceId, String ingotId, Instant ts, Map<String, Double> values) {
        // 爐子未運轉時，各感測值為 0 或負值，會大量撞出 ±3σ 而誤報 CRITICAL。
        // 那是閒置/換爐的雜訊、不是製程異常 → 這種情況仍記錄 SPC 違規，但不發 Slack。
        Double heaterTemp = values.get("heaterTemp");
        boolean furnaceRunning = heaterTemp != null && heaterTemp > RUNNING_HEATER_TEMP_C;

        for (Map.Entry<String, String> entry : SpcBaselineService.PARAM_COLUMN.entrySet()) {
            String paramName = entry.getKey();
            Double value = values.get(paramName);
            if (value == null) continue;

            Optional<SpcBaseline> baselineOpt = baselineRepo.findByFurnaceIdAndParamName(furnaceId, paramName);
            if (baselineOpt.isEmpty()) continue;

            SpcBaseline baseline = baselineOpt.get();
            List<SpcRuleEngine.Violation> violations = ruleEngine.check(furnaceId, paramName, value, baseline);

            for (SpcRuleEngine.Violation v : violations) {
                // Dedupe: 相同 furnace + param + rule 30 秒內只存一次
                String dedupeKey = furnaceId + ":" + paramName + ":" + v.getRuleId();
                Instant last = lastRecordTime.get(dedupeKey);
                if (last != null && ts.getEpochSecond() - last.getEpochSecond() < DEDUPE_SECONDS) {
                    continue;  // 太近、略過
                }
                lastRecordTime.put(dedupeKey, ts);

                try {
                    SpcViolation record = new SpcViolation();
                    record.setTs(ts);
                    record.setFurnaceId(furnaceId);
                    record.setIngotId(ingotId);
                    record.setParamName(paramName);
                    record.setRuleId(v.getRuleId());
                    record.setRuleName(v.getRuleName());
                    record.setValue(value);
                    record.setMean(baseline.getMean());
                    record.setStdDev(baseline.getStdDev());
                    record.setUcl3sigma(baseline.getUcl3sigma());
                    record.setLcl3sigma(baseline.getLcl3sigma());
                    record.setSeverity(v.getSeverity());
                    violationRepo.save(record);

                    // 發 Kafka
                    String alertJson = String.format(
                            "{\"ts\":\"%s\",\"furnaceId\":\"%s\",\"ingotId\":\"%s\",\"paramName\":\"%s\"," +
                                    "\"ruleId\":%d,\"ruleName\":\"%s\",\"value\":%.2f,\"severity\":\"%s\"}",
                            ts, furnaceId, ingotId, paramName,
                            v.getRuleId(), v.getRuleName(), value, v.getSeverity());
                    kafka.send(SPC_ALERTS_TOPIC, furnaceId, alertJson);

                    log.warn("SPC violation: {} {} rule={} value={} severity={}",
                            furnaceId, paramName, v.getRuleId(), String.format("%.2f", value), v.getSeverity());

                    // CRITICAL → 交給 AlarmService 發 Slack（Slack 只在 AlarmService 觸發）
                    // 護欄：爐子未運轉、或量測值為 0（感測器閒置）時不通知，避免洗版誤報
                    if ("CRITICAL".equalsIgnoreCase(v.getSeverity())) {
                        if (furnaceRunning && value != 0.0) {
                            alarmService.notifySpcCritical(
                                    furnaceId, ingotId, paramName, v.getRuleId(), v.getRuleName(),
                                    value, baseline.getMean(), baseline.getUcl3sigma(), baseline.getLcl3sigma());
                        } else {
                            log.debug("CRITICAL 但爐子未運轉或值為 0，略過 Slack: {} {} value={}",
                                    furnaceId, paramName, value);
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to save SPC violation: {}", e.getMessage());
                }
            }
        }
    }
}
