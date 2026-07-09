package com.twin.alarm.service;

import com.twin.alarm.entity.SpcBaseline;
import com.twin.alarm.entity.SpcViolation;
import com.twin.alarm.repository.SpcViolationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class SpcBackfillService {

    private static final Logger log = LoggerFactory.getLogger(SpcBackfillService.class);

    private final SpcBaselineService baselineService;
    private final SpcViolationRepository violationRepo;

    @PersistenceContext
    private EntityManager em;

    private static final double RUNNING_GATE_HEATER_TEMP = 500.0;
    private static final long DEDUPE_SECONDS = 300;

    public void backfillAll() {
        List<String> furnaces = List.of("D1", "D3", "DB", "F7", "FA");
        for (String furnaceId : furnaces) {
            for (String paramName : SpcBaselineService.PARAM_COLUMN.keySet()) {
                try {
                    backfillOne(furnaceId, paramName);
                } catch (Exception e) {
                    log.warn("Backfill failed furnace={} param={}: {}", furnaceId, paramName, e.getMessage());
                }
            }
        }
        log.info("SPC violation backfill done");
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public void backfillOne(String furnaceId, String paramName) {
        String column = SpcBaselineService.PARAM_COLUMN.get(paramName);
        if (column == null) return;

        var baselineOpt = baselineService.get(furnaceId, paramName);
        if (baselineOpt.isEmpty()) {
            log.warn("No baseline for furnace={} param={}, skip backfill", furnaceId, paramName);
            return;
        }
        SpcBaseline baseline = baselineOpt.get();

        // 拉歷史資料：只取「爐在運轉」的點、按時間升序（跟 buffer 累積順序一致）
        String sql = String.format("""
                SELECT time, %1$s
                FROM furnace_metrics
                WHERE furnace_id = ?1
                  AND time >= (SELECT MAX(time) FROM furnace_metrics WHERE furnace_id = ?1) - INTERVAL '7 days'
                  AND %1$s IS NOT NULL
                  AND heater_temp > ?2
                ORDER BY time ASC
                """, column);

        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter(1, furnaceId)
                .setParameter(2, RUNNING_GATE_HEATER_TEMP)
                .getResultList();

        if (rows.isEmpty()) return;

        // 每個 (furnace, param) 用全新獨立 engine、不共用即時 consumer 的 buffer
        SpcRuleEngine backfillEngine = new SpcRuleEngine();
        Map<String, Instant> lastRecordTime = new ConcurrentHashMap<>();
        int violationCount = 0;

        for (Object[] row : rows) {
            Instant ts = ((java.sql.Timestamp) row[0]).toInstant();
            double value = ((Number) row[1]).doubleValue();

            List<SpcRuleEngine.Violation> violations =
                    backfillEngine.check(furnaceId, paramName, value, baseline);

            for (SpcRuleEngine.Violation v : violations) {
                String dedupeKey = furnaceId + ":" + paramName + ":" + v.getRuleId();
                Instant last = lastRecordTime.get(dedupeKey);
                if (last != null && ts.getEpochSecond() - last.getEpochSecond() < DEDUPE_SECONDS) {
                    continue;
                }
                lastRecordTime.put(dedupeKey, ts);

                SpcViolation record = new SpcViolation();
                record.setTs(ts);
                record.setFurnaceId(furnaceId);
                record.setIngotId(null); // 歷史批次沒有逐點 ingotNo,先留空
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
                violationCount++;
            }
        }
        log.info("Backfilled furnace={} param={} points={} violations={}",
                furnaceId, paramName, rows.size(), violationCount);
    }
}
