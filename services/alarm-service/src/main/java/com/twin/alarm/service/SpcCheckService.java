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

/**
 * SPC 檢查。
 *
 * 兩個關鍵設計（與舊版不同）：
 *  1. 以「一分鐘子群平均」評估規則，而非逐筆原始讀值。
 *     baseline 的 σ 是用 time_bucket('1 minute') 的平均值估的，
 *     若拿去套在原始讀值上，σ 會被系統性低估數倍（平均會縮小變異），
 *     管制界過窄 → 8 條規則全面誤報。
 *  2. baseline 依 operation_mode 分別查找。各製程階段（MELT/CROWN/BODY…）
 *     的分佈本質不同，不能共用一組管制界。
 */
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

    /** 判定爐子「運轉中」的加熱器溫度門檻 */
    private static final double RUNNING_HEATER_TEMP_C = 20.0;

    /** 子群長度（秒），必須與 baseline 的 time_bucket('1 minute') 一致 */
    private static final long SUBGROUP_SECONDS = 60;

    /** 每個 furnace+param 的當前子群累積 */
    private static class Subgroup {
        final long minute;
        final String mode;
        final String ingotId;
        final Instant startedAt;
        double sum;
        int count;

        Subgroup(long minute, String mode, String ingotId, Instant startedAt) {
            this.minute = minute;
            this.mode = mode;
            this.ingotId = ingotId;
            this.startedAt = startedAt;
        }
    }

    private final Map<String, Subgroup> subgroups = new ConcurrentHashMap<>();
    private final Map<String, String> lastIngotByFurnace = new ConcurrentHashMap<>();
    private final Map<String, Boolean> lastRunningByFurnace = new ConcurrentHashMap<>();

    public void checkAllParams(String furnaceId, String ingotId, String operationMode,
                               Instant ts, Map<String, Double> values) {

        String ingot = (ingotId == null) ? "" : ingotId;

        // 換晶棒 → 清掉序列狀態（避免跨晶棒誤判趨勢）
        String prevIngot = lastIngotByFurnace.put(furnaceId, ingot);
        if (prevIngot != null && !prevIngot.equals(ingot)) {
            resetFurnaceState(furnaceId);
            log.info("換晶棒，重置 SPC 序列狀態: furnace={} {} -> {}", furnaceId, prevIngot, ingot);
        }

        Double heaterTemp = values.get("heaterTemp");
        boolean running = heaterTemp != null && heaterTemp > RUNNING_HEATER_TEMP_C;

        Boolean prevRunning = lastRunningByFurnace.put(furnaceId, running);
        if (Boolean.TRUE.equals(prevRunning) && !running) {
            // 由運轉轉為停爐 → 清狀態，復爐後不會把停機前後接成同一段序列
            resetFurnaceState(furnaceId);
            log.info("爐子停止運轉，重置 SPC 序列狀態: furnace={}", furnaceId);
        }

        // 未運轉不做 SPC：此時各感測值為 0 或負值，全是雜訊
        if (!running) return;
        if (operationMode == null || operationMode.isBlank()) return;

        long minute = ts.getEpochSecond() / SUBGROUP_SECONDS;

        for (String paramName : SpcBaselineService.PARAM_COLUMN.keySet()) {
            Double value = values.get(paramName);
            if (value == null) continue;

            String key = furnaceId + ":" + paramName;
            Subgroup sg = subgroups.get(key);

            // 分鐘切換或製程階段改變 → 先結算前一個子群，再開新的
            if (sg != null && (sg.minute != minute || !operationMode.equals(sg.mode))) {
                if (sg.count > 0) {
                    evaluate(furnaceId, sg.ingotId, sg.mode, paramName,
                            sg.sum / sg.count, sg.startedAt);
                }
                sg = null;
            }

            if (sg == null) {
                sg = new Subgroup(minute, operationMode, ingot, ts);
                subgroups.put(key, sg);
            }

            sg.sum += value;
            sg.count++;
        }
    }

    private void resetFurnaceState(String furnaceId) {
        ruleEngine.resetFurnace(furnaceId);
        subgroups.keySet().removeIf(k -> k.startsWith(furnaceId + ":"));
    }

    /** 對一個已完成的子群平均值跑規則 */
    private void evaluate(String furnaceId, String ingotId, String mode, String paramName,
                          double subgroupMean, Instant ts) {

        Optional<SpcBaseline> baselineOpt =
                baselineRepo.findByFurnaceIdAndParamNameAndOperationMode(furnaceId, paramName, mode);
        if (baselineOpt.isEmpty()) {
            // 該製程階段尚未建立 baseline → 不評估（不是錯誤）
            return;
        }
        SpcBaseline baseline = baselineOpt.get();

        List<SpcRuleEngine.Violation> violations =
                ruleEngine.check(furnaceId, paramName, mode, subgroupMean, baseline);

        for (SpcRuleEngine.Violation v : violations) {
            try {
                SpcViolation record = new SpcViolation();
                record.setTs(ts);
                record.setFurnaceId(furnaceId);
                record.setIngotId(ingotId);
                record.setParamName(paramName);
                record.setRuleId(v.getRuleId());
                record.setRuleName(v.getRuleName());
                record.setValue(subgroupMean);
                record.setMean(baseline.getMean());
                record.setStdDev(baseline.getStdDev());
                record.setUcl3sigma(baseline.getUcl3sigma());
                record.setLcl3sigma(baseline.getLcl3sigma());
                record.setSeverity(v.getSeverity());
                violationRepo.save(record);

                String alertJson = String.format(
                        "{\"ts\":\"%s\",\"furnaceId\":\"%s\",\"ingotId\":\"%s\",\"operationMode\":\"%s\"," +
                                "\"paramName\":\"%s\",\"ruleId\":%d,\"ruleName\":\"%s\"," +
                                "\"value\":%.2f,\"severity\":\"%s\"}",
                        ts, furnaceId, ingotId, mode, paramName,
                        v.getRuleId(), v.getRuleName(), subgroupMean, v.getSeverity());
                kafka.send(SPC_ALERTS_TOPIC, furnaceId, alertJson);

                log.warn("SPC violation: {} [{}] {} rule={} value={} severity={}",
                        furnaceId, mode, paramName, v.getRuleId(),
                        String.format("%.2f", subgroupMean), v.getSeverity());

                // CRITICAL → 交給 AlarmService 發 Slack（Slack 只在 AlarmService 觸發）
                if ("CRITICAL".equalsIgnoreCase(v.getSeverity())) {
                    alarmService.notifySpcCritical(
                            furnaceId, ingotId, paramName, v.getRuleId(), v.getRuleName(),
                            subgroupMean, baseline.getMean(),
                            baseline.getUcl3sigma(), baseline.getLcl3sigma());
                }
            } catch (Exception e) {
                log.error("Failed to save SPC violation: {}", e.getMessage());
            }
        }
    }
}
