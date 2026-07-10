package com.twin.alarm.service;

import com.twin.alarm.entity.OeeTarget;
import com.twin.alarm.repository.OeeTargetRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OeeService {

    private final OeeTargetRepository targetRepo;

    @PersistenceContext
    private EntityManager em;

    public OeeResult calculate(String furnaceId, int minutes) {
        Optional<OeeTarget> targetOpt = targetRepo.findById(furnaceId);
        if (targetOpt.isEmpty()) {
            throw new IllegalArgumentException("No OEE target configured for furnace: " + furnaceId);
        }
        OeeTarget target = targetOpt.get();

        double availability = calculateAvailability(furnaceId, minutes);
        Double performance = calculatePerformance(furnaceId, minutes, target.getTargetGrMean());
        QualityResult quality = calculateQuality(furnaceId, minutes, target.getTargetLengthMm(), target.getQualityThresholdPct());

        Double oee = (performance != null && quality.totalIngots > 0)
                ? availability * performance * quality.qualityRate
                : null;

        OeeResult result = new OeeResult();
        result.setFurnaceId(furnaceId);
        result.setAvailability(round(availability));
        result.setPerformance(performance != null ? round(performance) : null);
        result.setQuality(quality.totalIngots > 0 ? round(quality.qualityRate) : null);
        result.setOee(oee != null ? round(oee) : null);
        result.setGoodIngots(quality.goodIngots);
        result.setTotalIngots(quality.totalIngots);
        result.setTargetLengthMm(target.getTargetLengthMm());
        result.setTargetGrMean(target.getTargetGrMean());
        result.setTargetCycleHours(target.getTargetCycleHours());
        return result;
    }

    /**
     * Availability：以「有運轉的分鐘數 / 有資料的分鐘數」估算。
     * 改讀 furnace_metrics_1min 連續聚合（每分鐘一列，比 raw 少約 600 倍），
     * 避免對原始表做 LEAD 視窗全排序。同一分鐘可能有多列（不同 ingot/mode），
     * 先以 bucket 聚合，取該分鐘最高溫判定是否運轉（heater > 20°C）。
     */
    private double calculateAvailability(String furnaceId, int minutes) {
        String sql = """
                WITH per_min AS (
                    SELECT bucket, MAX(avg_heater_temp) AS ht
                    FROM furnace_metrics_1min
                    WHERE furnace_id = ?1
                      AND bucket >= NOW() - (?2 || ' minutes')::interval
                    GROUP BY bucket
                )
                SELECT
                    COALESCE(SUM(CASE WHEN ht > 20 THEN 1 ELSE 0 END), 0) AS running_minutes,
                    COUNT(*) AS total_minutes
                FROM per_min
                """;

        Object[] row = (Object[]) em.createNativeQuery(sql)
                .setParameter(1, furnaceId)
                .setParameter(2, minutes)
                .getSingleResult();

        double runningMinutes = ((Number) row[0]).doubleValue();
        double totalMinutes = ((Number) row[1]).doubleValue();

        return totalMinutes > 0 ? Math.min(runningMinutes / totalMinutes, 1.0) : 0.0;
    }

    /** Performance：BODY 階段平均拉速 / 目標拉速，封頂 100%；若無 BODY 階段資料則回傳 null。
     *  改讀 furnace_metrics_1min（各分鐘平均的平均，OEE 用途誤差可忽略）。 */
    private Double calculatePerformance(String furnaceId, int minutes, double targetGrMean) {
        String sql = """
                SELECT AVG(avg_gr)
                FROM furnace_metrics_1min
                WHERE furnace_id = ?1
                  AND operation_mode = 'BODY'
                  AND bucket >= NOW() - (?2 || ' minutes')::interval
                  AND avg_gr IS NOT NULL
                """;

        Object result = em.createNativeQuery(sql)
                .setParameter(1, furnaceId)
                .setParameter(2, minutes)
                .getSingleResult();

        if (result == null || targetGrMean <= 0) return null;
        double avgGrMean = ((Number) result).doubleValue();
        return Math.min(avgGrMean / targetGrMean, 1.0);
    }

    /** Quality：按 ingot_no 分組取最終長度，排除仍在進行中的最新一根，跟目標長度比較是否達標 */
    @SuppressWarnings("unchecked")
    private QualityResult calculateQuality(String furnaceId, int minutes, double targetLengthMm, double thresholdPct) {
        String sql = """
                WITH ingot_stats AS (
                    SELECT
                        ingot_no,
                        MAX(body_length) AS final_length,
                        MAX(time) AS last_seen
                    FROM furnace_metrics
                    WHERE furnace_id = ?1
                      AND time >= NOW() - (?2 || ' minutes')::interval
                      AND ingot_no IS NOT NULL
                      AND body_length IS NOT NULL
                    GROUP BY ingot_no
                ),
                latest_ingot AS (
                    SELECT ingot_no
                    FROM furnace_metrics
                    WHERE furnace_id = ?1
                    ORDER BY time DESC
                    LIMIT 1
                )
                SELECT s.ingot_no, s.final_length
                FROM ingot_stats s
                WHERE s.ingot_no != (SELECT ingot_no FROM latest_ingot)
                """;

        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter(1, furnaceId)
                .setParameter(2, minutes)
                .getResultList();

        int total = rows.size();
        int good = 0;
        for (Object[] row : rows) {
            double finalLength = ((Number) row[1]).doubleValue();
            if (finalLength / targetLengthMm >= thresholdPct) {
                good++;
            }
        }

        QualityResult qr = new QualityResult();
        qr.totalIngots = total;
        qr.goodIngots = good;
        qr.qualityRate = total > 0 ? (double) good / total : 0.0;
        return qr;
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static class QualityResult {
        int totalIngots;
        int goodIngots;
        double qualityRate;
    }

    @Getter
    @Setter
    public static class OeeResult {
        private String furnaceId;
        private Double availability;
        private Double performance;
        private Double quality;
        private Double oee;
        private Integer goodIngots;
        private Integer totalIngots;
        private Double targetLengthMm;
        private Double targetGrMean;
        private Double targetCycleHours;
    }
}
