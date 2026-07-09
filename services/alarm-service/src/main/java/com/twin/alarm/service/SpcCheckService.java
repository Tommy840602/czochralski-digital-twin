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

    public static final String SPC_ALERTS_TOPIC = "spc-alerts";

    /** 同一 furnace+param+rule 在 DEDUPE_SECONDS 秒內只寫一次、避免 DB 爆炸 */
    private static final long DEDUPE_SECONDS = 300;
    private final Map<String, Instant> lastRecordTime = new ConcurrentHashMap<>();

    public void checkAllParams(String furnaceId, String ingotId, Instant ts, Map<String, Double> values) {
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
                } catch (Exception e) {
                    log.error("Failed to save SPC violation: {}", e.getMessage());
                }
            }
        }
    }
}
