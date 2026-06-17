package com.twin.furnace.repository;

import com.twin.furnace.model.FurnaceMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * furnace_metrics TimescaleDB 查詢。
 * 核心查詢都走 Native SQL，直接使用 TimescaleDB 的聚合 view。
 */
@Repository
public interface FurnaceMetricsRepository extends JpaRepository<FurnaceMetrics, Long> {

    // ── 即時查詢 ──────────────────────────────────────────────────────────

    /** 取某台爐子最新一筆數據 */
    @Query(value = """
        SELECT * FROM furnace_metrics
        WHERE furnace_id = :furnaceId
        ORDER BY time DESC LIMIT 1
        """, nativeQuery = true)
    Optional<FurnaceMetrics> findLatestByFurnaceId(@Param("furnaceId") String furnaceId);

    /** 取所有爐子最新一筆（用 DISTINCT ON，TimescaleDB 最佳化） */
    @Query(value = """
        SELECT DISTINCT ON (furnace_id) *
        FROM furnace_metrics
        ORDER BY furnace_id, time DESC
        """, nativeQuery = true)
    List<FurnaceMetrics> findAllLatest();

    // ── 歷史查詢 ──────────────────────────────────────────────────────────

    /**
     * 取某台爐子在時間範圍內的原始數據
     * 預設最多回傳 10,000 筆，前端分頁用
     */
    @Query(value = """
        SELECT * FROM furnace_metrics
        WHERE furnace_id = :furnaceId
          AND time BETWEEN :from AND :to
        ORDER BY time ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<FurnaceMetrics> findHistory(
            @Param("furnaceId") String furnaceId,
            @Param("from")      OffsetDateTime from,
            @Param("to")        OffsetDateTime to,
            @Param("limit")     int limit);

    /**
     * 取 1 分鐘聚合（來自連續聚合 view，極快）
     * 供前端趨勢圖使用
     */
    @Query(value = """
        SELECT
            bucket                          AS time,
            furnace_id,
            ingot_no,
            operation_mode,
            avg_diameter                    AS diameter,
            avg_heater_temp                 AS heater_temp,
            avg_power                       AS heater_power_sv,
            avg_gr                          AS gr_mean,
            avg_body_length                 AS body_length,
            avg_residual_weight             AS residual_weight,
            NULL AS d_mean, NULL AS diameter_target,
            NULL AS heater_temp_target, NULL AS ht_mean,
            NULL AS temp2, NULL AS temp4, NULL AS temp5,
            NULL AS temp9, NULL AS temp29,
            NULL AS neck_length_accum,
            NULL AS seed_lift, NULL AS seed_lift_sp, NULL AS seed_lift_target,
            NULL AS seed_rotation_sp,
            NULL AS crucible_rotation_sp, NULL AS cr_mean,
            NULL AS crucible_lift, NULL AS crucible_lift_ratio,
            NULL AS crucible_position, NULL AS crucible_pos_calibrated,
            NULL AS ctpfl_pul, NULL AS magnet_pv,
            NULL AS argon_flow_rate, NULL AS lower_chamber_press,
            NULL AS lower_chamber_press_sp, NULL AS thro_valve_open,
            NULL AS bp_mean, NULL AS bpu60mean,
            NULL AS btpl_bpul1, NULL AS btpl_bpll1,
            NULL AS pidsl_ddmean, NULL AS pidsl_temp1,
            NULL AS countb, NULL AS sop,
            NULL AS id, NOW() AS created_at
        FROM furnace_metrics_1min
        WHERE furnace_id = :furnaceId
          AND bucket BETWEEN :from AND :to
        ORDER BY bucket ASC
        """, nativeQuery = true)
    List<FurnaceMetrics> findHistory1Min(
            @Param("furnaceId") String furnaceId,
            @Param("from")      OffsetDateTime from,
            @Param("to")        OffsetDateTime to);

    /**
     * 取 1 小時聚合（長期趨勢）
     */
    @Query(value = """
        SELECT
            bucket                          AS time,
            furnace_id,
            ingot_no,
            NULL AS operation_mode, NULL AS sop,
            avg_diameter                    AS diameter,
            NULL AS d_mean,
            NULL AS diameter_target,
            avg_heater_temp                 AS heater_temp,
            NULL AS heater_temp_target,
            avg_power                       AS heater_power_sv,
            NULL AS ht_mean,
            NULL AS temp2, NULL AS temp4, NULL AS temp5,
            NULL AS temp9, NULL AS temp29,
            avg_gr                          AS gr_mean,
            avg_body_length                 AS body_length,
            NULL AS neck_length_accum,
            NULL AS seed_lift, NULL AS seed_lift_sp, NULL AS seed_lift_target,
            NULL AS seed_rotation_sp,
            NULL AS crucible_rotation_sp, NULL AS cr_mean,
            NULL AS crucible_lift, NULL AS crucible_lift_ratio,
            NULL AS crucible_position, NULL AS crucible_pos_calibrated,
            NULL AS ctpfl_pul, NULL AS magnet_pv,
            NULL AS argon_flow_rate, NULL AS lower_chamber_press,
            NULL AS lower_chamber_press_sp, NULL AS thro_valve_open,
            NULL AS bp_mean, NULL AS bpu60mean,
            NULL AS btpl_bpul1, NULL AS btpl_bpll1,
            NULL AS pidsl_ddmean, NULL AS pidsl_temp1,
            avg_residual_weight             AS residual_weight,
            NULL AS countb,
            NULL AS id, NOW() AS created_at
        FROM furnace_metrics_1hour
        WHERE furnace_id = :furnaceId
          AND bucket BETWEEN :from AND :to
        ORDER BY bucket ASC
        """, nativeQuery = true)
    List<FurnaceMetrics> findHistory1Hour(
            @Param("furnaceId") String furnaceId,
            @Param("from")      OffsetDateTime from,
            @Param("to")        OffsetDateTime to);

    // ── 統計查詢 ──────────────────────────────────────────────────────────

    /** 各爐子資料筆數統計 */
    @Query(value = """
        SELECT furnace_id, COUNT(*) AS cnt
        FROM furnace_metrics
        GROUP BY furnace_id
        ORDER BY furnace_id
        """, nativeQuery = true)
    List<Object[]> countByFurnace();

    /** 取各爐子最近 N 筆（供 WebSocket 推送初始化） */
    @Query(value = """
        SELECT * FROM furnace_metrics
        WHERE furnace_id = :furnaceId
        ORDER BY time DESC LIMIT :n
        """, nativeQuery = true)
    List<FurnaceMetrics> findRecentN(
            @Param("furnaceId") String furnaceId,
            @Param("n")         int n);
}