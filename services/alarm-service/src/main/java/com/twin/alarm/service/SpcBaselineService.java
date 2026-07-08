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

    /** 對外提供的 6 個 SPC 監測參數 → DB 欄位名 */
    public static final Map<String, String> PARAM_COLUMN = Map.of(
            "heaterTemp", "heater_temp",
            "diameter", "diameter",
            "grMean", "gr_mean",
            "heaterPowerSv", "heater_power_sv",
            "seedLift", "seed_lift",
            "bodyLength", "body_length"
    );

    /** 服務啟動就跑一次（如果 baseline 表是空的）+ 每天凌晨 3 點重算 */
    @Scheduled(cron = "0 0 3 * * *")
    public void scheduledRebuild() {
        rebuildAll();
    }

    public void rebuildAll() {
        List<String> furnaces = List.of("D1", "D3", "DB", "F7", "FA");
        for (String furnaceId : furnaces) {
            for (String paramName : PARAM_COLUMN.keySet()) {
                try {
                    rebuild(furnaceId, paramName);
                } catch (Exception e) {
                    log.warn("Failed to rebuild baseline furnace={} param={}: {}",
                            furnaceId, paramName, e.getMessage());
                }
            }
        }
        log.info("Baseline rebuild done");
    }

    private static final double RUNNING_GATE_HEATER_TEMP = 500.0;

    @Transactional
    public SpcBaseline rebuild(String furnaceId, String paramName) {
        String column = PARAM_COLUMN.get(paramName);
        if (column == null) {
            throw new IllegalArgumentException("Unknown paramName: " + paramName);
        }

        // 用 time_bucket 先降採樣成每分鐘平均值，避免對百萬級原始資料做 window function 排序
        String sql = String.format("""
            WITH bucketed AS (
                SELECT
                    time_bucket('1 minute', time) AS bucket_time,
                    AVG(%1$s) AS v
                FROM furnace_metrics
                WHERE furnace_id = ?1
                  AND time >= (SELECT MAX(time) FROM furnace_metrics WHERE furnace_id = ?1)
                             - INTERVAL '7 days'
                  AND %1$s IS NOT NULL
                  AND heater_temp > ?2
                GROUP BY bucket_time
            ),
            mr AS (
                SELECT v, ABS(v - LAG(v) OVER (ORDER BY bucket_time)) AS moving_range
                FROM bucketed
            )
            SELECT AVG(v), AVG(moving_range) / 1.128, COUNT(*)
            FROM mr
            """, column);

        Object[] row = (Object[]) em.createNativeQuery(sql)
                .setParameter(1, furnaceId)
                .setParameter(2, RUNNING_GATE_HEATER_TEMP)
                .getSingleResult();

        Double mean = row[0] == null ? null : ((Number) row[0]).doubleValue();
        Double std  = row[1] == null ? null : ((Number) row[1]).doubleValue();
        Long count  = row[2] == null ? 0L : ((Number) row[2]).longValue();

        if (mean == null || std == null || std == 0 || count < 30) {
            log.warn("Not enough steady-state data furnace={} param={} count={}",
                    furnaceId, paramName, count);
            return null;
        }

        SpcBaseline b = baselineRepo.findByFurnaceIdAndParamName(furnaceId, paramName)
                .orElseGet(SpcBaseline::new);
        b.setFurnaceId(furnaceId);
        b.setParamName(paramName);
        b.setMean(mean);
        b.setStdDev(std);
        b.setUcl3sigma(mean + 3 * std);
        b.setLcl3sigma(mean - 3 * std);
        b.setUcl2sigma(mean + 2 * std);
        b.setLcl2sigma(mean - 2 * std);
        b.setUcl1sigma(mean + std);
        b.setLcl1sigma(mean - std);
        b.setSampleSize(count.intValue());
        b.setCalculatedAt(Instant.now());
        return baselineRepo.save(b);
    }

    public List<SpcBaseline> listByFurnace(String furnaceId) {
        return baselineRepo.findByFurnaceId(furnaceId);
    }

    public Optional<SpcBaseline> get(String furnaceId, String paramName) {
        return baselineRepo.findByFurnaceIdAndParamName(furnaceId, paramName);
    }
}
