package com.twin.alarm.controller;

import com.twin.alarm.service.SpcBaselineService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SPC 管制圖的資料點：最近的即時資料，連續不中斷。
 *
 *  - 一分鐘子群平均（與 baseline 估 σ、規則引擎評估的尺度一致）
 *  - 只取爐在運轉的資料
 *  - 不依 operation_mode 過濾：SPC 要看的就是即時的製程走勢
 *
 * 每個點會附上它所屬階段的管制界（由 spc_baseline join 出來），前端畫成階梯線——
 * 製程階段一換，管制界就跟著跳。非穩態階段沒有 baseline，該段就沒有界（斷開），
 * 這是誠實的表達：那些階段不適用 SPC。
 */
@RestController
@RequestMapping("/spc")
@RequiredArgsConstructor
public class SpcTimeseriesController {

    private static final Logger log = LoggerFactory.getLogger(SpcTimeseriesController.class);

    private static final int MAX_POINTS = 500;

    @PersistenceContext
    private EntityManager em;

    @GetMapping("/timeseries")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> timeseries(
            @RequestParam String furnaceId,
            @RequestParam String paramName,
            @RequestParam(defaultValue = "120") int points) {

        String column = SpcBaselineService.PARAM_CAGG_COLUMN.get(paramName);
        if (column == null) {
            throw new IllegalArgumentException("Unknown paramName: " + paramName);
        }
        int limit = Math.min(Math.max(points, 1), MAX_POINTS);

        // 只取「最近 windowMinutes 分鐘」之內的子群：
        // 停爐的分鐘會被 running gate 濾掉，若只用 LIMIT N 取最後 N 個有效子群，
        // 這 N 個點可能橫跨好幾小時，x 軸會一直留著很舊的時間。加上時間窗，舊的就會自動掉出去。
        int windowMinutes = limit;

        // 讀連續聚合（每分鐘一列），不掃原始表——掃原始表會把 Postgres OOM 掉。
        // 每個 1 分鐘子群取樣本數最多的 operation_mode 當作該分鐘的階段，
        // 再 join 出該階段的管制界。
        String sql = String.format("""
                SELECT t.ts, t.value, t.mode,
                       b.mean, b.ucl_3sigma, b.lcl_3sigma,
                       b.ucl_2sigma, b.lcl_2sigma, b.ucl_1sigma, b.lcl_1sigma
                FROM (
                    SELECT bucket AS ts,
                           SUM(%1$s * sample_count) / NULLIF(SUM(sample_count), 0) AS value,
                           (array_agg(operation_mode ORDER BY sample_count DESC))[1] AS mode
                    FROM furnace_metrics_1min
                    WHERE furnace_id = ?1
                      AND bucket >= (SELECT MAX(bucket) FROM furnace_metrics_1min WHERE furnace_id = ?1)
                                   - INTERVAL '%4$d minutes'
                      AND %1$s IS NOT NULL
                      AND avg_heater_temp > %2$s
                    GROUP BY bucket
                    ORDER BY bucket DESC
                    LIMIT %3$d
                ) t
                LEFT JOIN spc_baseline b
                       ON b.furnace_id = ?1
                      AND b.param_name = ?2
                      AND b.operation_mode = t.mode
                ORDER BY t.ts ASC
                """, column, SpcBaselineService.RUNNING_GATE_HEATER_TEMP, limit, windowMinutes);

        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter(1, furnaceId)
                .setParameter(2, paramName)
                .getResultList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> point = new HashMap<>();
            point.put("ts", row[0]);
            point.put("value", num(row[1]));
            point.put("mode", row[2]);
            // 該點所屬階段的管制界；非穩態階段沒有 baseline → null，前端不畫
            point.put("mean", num(row[3]));
            point.put("ucl3sigma", num(row[4]));
            point.put("lcl3sigma", num(row[5]));
            point.put("ucl2sigma", num(row[6]));
            point.put("lcl2sigma", num(row[7]));
            point.put("ucl1sigma", num(row[8]));
            point.put("lcl1sigma", num(row[9]));
            result.add(point);
        }
        return result;
    }

    private static Double num(Object o) {
        return (o == null) ? null : ((Number) o).doubleValue();
    }

    /**
     * 爐子「當下」的製程階段——取最新一筆原始讀值，與數位孿生同一個來源。
     *
     * 不能用連續聚合的最後一個子群來判斷：1 分鐘子群本身就有最多一分鐘的延遲，
     * 而且跨越階段切換的那一分鐘會取「樣本較多的那個 mode」，導致顯示的階段落後於實際。
     */
    @GetMapping("/current-mode")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public Map<String, Object> currentMode(@RequestParam String furnaceId) {
        String sql = """
                SELECT operation_mode
                FROM furnace_metrics
                WHERE furnace_id = ?1
                  AND time >= NOW() - INTERVAL '1 hour'
                  AND operation_mode IS NOT NULL
                ORDER BY time DESC
                LIMIT 1
                """;
        List<?> rows = em.createNativeQuery(sql)
                .setParameter(1, furnaceId)
                .getResultList();

        Map<String, Object> result = new HashMap<>();
        result.put("mode", rows.isEmpty() ? null : rows.get(0));
        return result;
    }
}
