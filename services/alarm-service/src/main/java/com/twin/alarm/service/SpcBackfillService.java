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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 以歷史資料回填 SPC 違規。
 *
 * 必須與即時路徑（SpcCheckService）用完全相同的尺度與語意，否則兩邊統計對不起來：
 *  - 一律以「每分鐘子群平均」評估，不用原始逐筆讀值（baseline 的 σ 是用分鐘平均估的）
 *  - baseline 依 operation_mode 分別取用
 *  - 違規由 SpcRuleEngine 做邊緣觸發，不需要額外的時間 dedupe
 */
@Service
@RequiredArgsConstructor
public class SpcBackfillService {

    private static final Logger log = LoggerFactory.getLogger(SpcBackfillService.class);

    private final SpcBaselineService baselineService;
    private final SpcViolationRepository violationRepo;

    @PersistenceContext
    private EntityManager em;

    private static final double RUNNING_GATE_HEATER_TEMP = 500.0;

    public void backfillAll() {
        List<String> furnaces = List.of("D1", "D3", "DB", "F7", "FA");
        for (String furnaceId : furnaces) {
            backfillFurnace(furnaceId);
        }
        log.info("SPC violation backfill done (all furnaces)");
    }

    /**
     * 回填某爐：每個參數一支查詢，一次撈回所有製程階段的分鐘子群。
     * （原本每個 mode 各掃一次全表，11 個 mode 就要掃 11 次，非常慢。）
     */
    @SuppressWarnings("unchecked")
    @Transactional
    public void backfillFurnace(String furnaceId) {
        for (String paramName : SpcBaselineService.PARAM_CAGG_COLUMN.keySet()) {
            String column = SpcBaselineService.PARAM_CAGG_COLUMN.get(paramName);
            if (column == null) continue;

            try {
                // 讀連續聚合，不掃原始表（原始表會把 Postgres OOM 掉）
                String sql = String.format("""
                        SELECT operation_mode,
                               bucket AS bucket_time,
                               SUM(%1$s * sample_count) / NULLIF(SUM(sample_count), 0) AS v
                        FROM furnace_metrics_1min
                        WHERE furnace_id = ?1
                          AND bucket >= (SELECT MAX(bucket) FROM furnace_metrics_1min WHERE furnace_id = ?1)
                                       - INTERVAL '7 days'
                          AND %1$s IS NOT NULL
                          AND avg_heater_temp > ?2
                          AND operation_mode IS NOT NULL
                        GROUP BY operation_mode, bucket
                        ORDER BY operation_mode, bucket ASC
                        """, column);

                List<Object[]> rows = em.createNativeQuery(sql)
                        .setParameter(1, furnaceId)
                        .setParameter(2, RUNNING_GATE_HEATER_TEMP)
                        .getResultList();

                // 每個 mode 一組獨立的 engine（buffer / 邊緣觸發狀態不可跨 mode 共用）
                Map<String, SpcRuleEngine> engines = new HashMap<>();
                Map<String, SpcBaseline> baselines = new HashMap<>();
                int violationCount = 0;

                for (Object[] row : rows) {
                    String mode = (String) row[0];
                    Instant ts = toInstant(row[1]);
                    double subgroupMean = ((Number) row[2]).doubleValue();

                    SpcBaseline baseline = baselines.computeIfAbsent(mode, m ->
                            baselineService.get(furnaceId, paramName, m).orElse(null));
                    if (baseline == null) continue;   // 該階段沒 baseline（資料不足）→ 跳過

                    SpcRuleEngine engine =
                            engines.computeIfAbsent(mode, m -> new SpcRuleEngine());

                    violationCount += record(engine, baseline, furnaceId, paramName,
                            mode, subgroupMean, ts);
                }
                log.info("Backfilled furnace={} param={} subgroups={} violations={}",
                        furnaceId, paramName, rows.size(), violationCount);
            } catch (Exception e) {
                log.warn("Backfill failed furnace={} param={}: {}", furnaceId, paramName, e.getMessage());
            }
        }
    }

    /**
     * 把 native query 回傳的時間欄位轉成 Instant。
     * 原始表的 TIMESTAMPTZ 會回 java.sql.Timestamp，但連續聚合的 bucket 會回 java.time.Instant
     * 或 OffsetDateTime——寫死轉型會炸 ClassCastException。
     */
    private static Instant toInstant(Object ts) {
        if (ts instanceof Instant i) return i;
        if (ts instanceof java.sql.Timestamp t) return t.toInstant();
        if (ts instanceof java.time.OffsetDateTime o) return o.toInstant();
        if (ts instanceof java.time.LocalDateTime l) return l.toInstant(java.time.ZoneOffset.UTC);
        throw new IllegalStateException("無法解析的時間型別: " + ts.getClass().getName());
    }

    /** 跑規則並寫入違規，回傳這次寫了幾筆 */
    private int record(SpcRuleEngine engine, SpcBaseline baseline, String furnaceId,
                       String paramName, String mode, double subgroupMean, Instant ts) {
        List<SpcRuleEngine.Violation> violations =
                engine.check(furnaceId, paramName, mode, subgroupMean, baseline);

        for (SpcRuleEngine.Violation v : violations) {
            SpcViolation r = new SpcViolation();
            r.setTs(ts);
            r.setFurnaceId(furnaceId);
            r.setIngotId(null);
            r.setParamName(paramName);
            r.setRuleId(v.getRuleId());
            r.setRuleName(v.getRuleName());
            r.setValue(subgroupMean);
            r.setMean(baseline.getMean());
            r.setStdDev(baseline.getStdDev());
            r.setUcl3sigma(baseline.getUcl3sigma());
            r.setLcl3sigma(baseline.getLcl3sigma());
            r.setSeverity(v.getSeverity());
            violationRepo.save(r);
        }
        return violations.size();
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public void backfillOne(String furnaceId, String paramName, String mode) {
        String column = SpcBaselineService.PARAM_CAGG_COLUMN.get(paramName);
        if (column == null) return;

        Optional<SpcBaseline> baselineOpt = baselineService.get(furnaceId, paramName, mode);
        if (baselineOpt.isEmpty()) {
            log.warn("No baseline for furnace={} param={} mode={}, skip backfill",
                    furnaceId, paramName, mode);
            return;
        }
        SpcBaseline baseline = baselineOpt.get();

        // 讀連續聚合：只取該 mode、且爐在運轉的分鐘子群，時間升序（與即時累積順序一致）
        String sql = String.format("""
                SELECT bucket AS bucket_time,
                       SUM(%1$s * sample_count) / NULLIF(SUM(sample_count), 0) AS v
                FROM furnace_metrics_1min
                WHERE furnace_id = ?1
                  AND bucket >= (SELECT MAX(bucket) FROM furnace_metrics_1min WHERE furnace_id = ?1)
                               - INTERVAL '7 days'
                  AND %1$s IS NOT NULL
                  AND avg_heater_temp > ?2
                  AND operation_mode = ?3
                GROUP BY bucket
                ORDER BY bucket ASC
                """, column);

        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter(1, furnaceId)
                .setParameter(2, RUNNING_GATE_HEATER_TEMP)
                .setParameter(3, mode)
                .getResultList();

        if (rows.isEmpty()) return;

        // 用全新獨立 engine，不共用即時 consumer 的 buffer / 邊緣觸發狀態
        SpcRuleEngine backfillEngine = new SpcRuleEngine();
        int violationCount = 0;

        for (Object[] row : rows) {
            Instant ts = toInstant(row[0]);
            double subgroupMean = ((Number) row[1]).doubleValue();

            violationCount += record(backfillEngine, baseline, furnaceId, paramName,
                    mode, subgroupMean, ts);
        }
        log.info("Backfilled furnace={} param={} mode={} subgroups={} violations={}",
                furnaceId, paramName, mode, rows.size(), violationCount);
    }
}
