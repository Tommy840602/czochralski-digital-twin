package com.twin.alarm.service;

import com.twin.alarm.entity.SpcBaseline;
import com.twin.alarm.repository.SpcBaselineRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SpcBaselineService {

    private static final Logger log = LoggerFactory.getLogger(SpcBaselineService.class);

    private final SpcBaselineRepository baselineRepo;

    @PersistenceContext
    private EntityManager em;

    /** 對外提供的 6 個 SPC 監測參數 → furnace_metrics 原始欄位名 */
    public static final Map<String, String> PARAM_COLUMN = Map.of(
            "heaterTemp", "heater_temp",
            "diameter", "diameter",
            "grMean", "gr_mean",
            "heaterPowerSv", "heater_power_sv",
            "seedLift", "seed_lift",
            "bodyLength", "body_length"
    );

    /**
     * 同樣 6 個參數 → furnace_metrics_1min 連續聚合的欄位名。
     *
     * baseline / 回填 / 管制圖一律讀這個連續聚合，不掃原始表：
     * 原始表單爐 7 天約 60 萬筆，做 time_bucket 聚合會把 Postgres 後端 OOM 掉（signal 9）。
     * 連續聚合已經存好每分鐘平均，掃描量降到約 1 萬筆。
     */
    public static final Map<String, String> PARAM_CAGG_COLUMN = Map.of(
            "heaterTemp", "avg_heater_temp",
            "diameter", "avg_diameter",
            "grMean", "avg_gr",
            "heaterPowerSv", "avg_power",
            "seedLift", "avg_seed_lift",
            "bodyLength", "avg_body_length"
    );

    /** 服務啟動就跑一次（如果 baseline 表是空的）+ 每天凌晨 3 點重算 */
    @Scheduled(cron = "0 0 3 * * *")
    public void scheduledRebuild() {
        rebuildAll();
    }

    public void rebuildAll() {
        List<String> furnaces = List.of("D1", "D3", "DB", "F7", "FA");
        for (String furnaceId : furnaces) {
            rebuildFurnace(furnaceId);
        }
        log.info("Baseline rebuild done (all furnaces)");
    }

    /** 判定「爐在運轉」的加熱器溫度門檻（baseline / 回填 / 圖表都必須用同一個） */
    public static final double RUNNING_GATE_HEATER_TEMP = 500.0;

    /** 一個 mode 至少要有這麼多分鐘的資料，才有統計意義 */
    private static final int MIN_SUBGROUPS = 30;

    /**
     * 變異係數（σ / |mean|）上限。
     *
     * CROWN / NECK4 / MELT BACK 這類過渡階段，製程本來就在爬升，不是穩態；
     * 對一個持續變動的量用 moving range 估 σ，會得到大到荒謬的值
     * （實測 heaterTemp 在過渡階段的 ±3σ 可橫跨 1500°C，而 BODY 只有 ±30°C）。
     * SPC 的前提是製程處於統計管制狀態——不滿足就不該建管制界，
     * 否則畫出來是一堆沒有意義的巨大管制帶，還會製造大量假違規。
     */
    private static final double MAX_CV = 0.10;

    /**
     * 找出某爐近 7 天實際出現過的製程階段（只看運轉中的資料）。
     * 不寫死 mode 清單，資料有什麼就建什麼。
     */
    public List<String> listModes(String furnaceId) {
        @SuppressWarnings("unchecked")
        List<String> modes = em.createNativeQuery("""
                SELECT DISTINCT operation_mode
                FROM furnace_metrics_1min
                WHERE furnace_id = ?1
                  AND bucket >= (SELECT MAX(bucket) FROM furnace_metrics_1min WHERE furnace_id = ?1)
                               - INTERVAL '7 days'
                  AND operation_mode IS NOT NULL
                  AND avg_heater_temp > ?2
                """)
                .setParameter(1, furnaceId)
                .setParameter(2, RUNNING_GATE_HEATER_TEMP)
                .getResultList();
        return modes;
    }

    /** 依 mean / σ / 寬鬆度倍數寫入 1σ、2σ、3σ 管制界 */
    private void applyLimits(SpcBaseline b, double mean, double std, double multiplier) {
        b.setSigmaMultiplier(multiplier);
        b.setUcl3sigma(mean + 3 * multiplier * std);
        b.setLcl3sigma(mean - 3 * multiplier * std);
        b.setUcl2sigma(mean + 2 * multiplier * std);
        b.setLcl2sigma(mean - 2 * multiplier * std);
        b.setUcl1sigma(mean + multiplier * std);
        b.setLcl1sigma(mean - multiplier * std);
    }

    @Transactional
    public SpcBaseline adjustSigmaMultiplier(String furnaceId, String paramName,
                                             String mode, double multiplier) {
        SpcBaseline b = baselineRepo
                .findByFurnaceIdAndParamNameAndOperationMode(furnaceId, paramName, mode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No baseline found for " + furnaceId + "/" + paramName + "/" + mode));

        applyLimits(b, b.getMean(), b.getStdDev(), multiplier);  // 原始 σ 不會被覆蓋
        return baselineRepo.save(b);
    }

    public List<SpcBaseline> listByFurnace(String furnaceId) {
        return baselineRepo.findByFurnaceId(furnaceId);
    }

    public Optional<SpcBaseline> get(String furnaceId, String paramName, String mode) {
        return baselineRepo.findByFurnaceIdAndParamNameAndOperationMode(furnaceId, paramName, mode);
    }

    /**
     * 重算某爐某參數在「所有製程階段」的 baseline —— 一支 SQL 一次算完。
     *
     * 用 PARTITION BY operation_mode 讓 moving range 只在同一階段內計算，
     * 避免對每個 mode 各掃一次全表（原本 11 個 mode 就要掃 11 次）。
     */
    @Transactional
    public int rebuildFurnaceParam(String furnaceId, String paramName) {
        String column = PARAM_CAGG_COLUMN.get(paramName);
        if (column == null) {
            throw new IllegalArgumentException("Unknown paramName: " + paramName);
        }

        // 讀連續聚合（每分鐘一列），不掃原始表。
        // CAGG 的 group by 含 ingot_no，同一分鐘可能有多列 → 依 sample_count 加權合併回單一分鐘平均。
        String sql = String.format("""
            WITH bucketed AS (
                SELECT
                    bucket AS bucket_time,
                    operation_mode,
                    SUM(%1$s * sample_count) / NULLIF(SUM(sample_count), 0) AS v
                FROM furnace_metrics_1min
                WHERE furnace_id = ?1
                  AND bucket >= (SELECT MAX(bucket) FROM furnace_metrics_1min WHERE furnace_id = ?1)
                               - INTERVAL '7 days'
                  AND %1$s IS NOT NULL
                  AND avg_heater_temp > ?2
                  AND operation_mode IS NOT NULL
                GROUP BY bucket, operation_mode
            ),
            mr AS (
                SELECT
                    operation_mode,
                    v,
                    ABS(v - LAG(v) OVER (PARTITION BY operation_mode ORDER BY bucket_time))
                        AS moving_range
                FROM bucketed
            )
            SELECT operation_mode, AVG(v), AVG(moving_range) / 1.128, COUNT(*)
            FROM mr
            GROUP BY operation_mode
            """, column);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter(1, furnaceId)
                .setParameter(2, RUNNING_GATE_HEATER_TEMP)
                .getResultList();

        int built = 0;
        for (Object[] row : rows) {
            String mode = (String) row[0];
            Double mean = row[1] == null ? null : ((Number) row[1]).doubleValue();
            Double std  = row[2] == null ? null : ((Number) row[2]).doubleValue();
            long count  = row[3] == null ? 0L : ((Number) row[3]).longValue();

            if (mode == null || mean == null || std == null || std == 0 || count < MIN_SUBGROUPS) {
                log.debug("Skip baseline furnace={} param={} mode={} count={} (資料不足)",
                        furnaceId, paramName, mode, count);
                continue;
            }

            // 非穩態階段（σ 相對均值過大）→ 不建管制界
            double cv = Math.abs(mean) > 1e-9 ? (std / Math.abs(mean)) : Double.MAX_VALUE;
            if (cv > MAX_CV) {
                log.info("Skip baseline furnace={} param={} mode={}: 非穩態階段 (σ={}, mean={}, CV={}%)",
                        furnaceId, paramName, mode,
                        String.format("%.3f", std), String.format("%.2f", mean),
                        String.format("%.1f", cv * 100));
                // 之前若建過（例如門檻調整前），一併移除，避免留下無意義的舊管制界
                baselineRepo.findByFurnaceIdAndParamNameAndOperationMode(furnaceId, paramName, mode)
                        .ifPresent(baselineRepo::delete);
                continue;
            }

            SpcBaseline b = baselineRepo
                    .findByFurnaceIdAndParamNameAndOperationMode(furnaceId, paramName, mode)
                    .orElseGet(SpcBaseline::new);

            double multiplier = (b.getSigmaMultiplier() == null) ? 1.0 : b.getSigmaMultiplier();

            b.setFurnaceId(furnaceId);
            b.setParamName(paramName);
            b.setOperationMode(mode);
            b.setMean(mean);
            b.setStdDev(std);
            applyLimits(b, mean, std, multiplier);
            b.setSampleSize((int) count);
            b.setCalculatedAt(Instant.now());
            baselineRepo.save(b);
            built++;
        }
        return built;
    }

    /** 重算某爐：6 個參數各一支查詢，每支一次算完所有製程階段 */
    public void rebuildFurnace(String furnaceId) {
        int total = 0;
        for (String paramName : PARAM_COLUMN.keySet()) {
            try {
                total += rebuildFurnaceParam(furnaceId, paramName);
            } catch (Exception e) {
                log.warn("Failed to rebuild baseline furnace={} param={}: {}",
                        furnaceId, paramName, e.getMessage());
            }
        }
        log.info("Baseline rebuild done for furnace={} baselines={}", furnaceId, total);
    }
}
