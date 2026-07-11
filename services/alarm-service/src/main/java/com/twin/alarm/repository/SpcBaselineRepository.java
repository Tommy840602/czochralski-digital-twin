package com.twin.alarm.repository;

import com.twin.alarm.entity.SpcBaseline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpcBaselineRepository extends JpaRepository<SpcBaseline, Long> {

    /** baseline 以 (furnace, param, operationMode) 為唯一鍵 */
    Optional<SpcBaseline> findByFurnaceIdAndParamNameAndOperationMode(
            String furnaceId, String paramName, String operationMode);

    List<SpcBaseline> findByFurnaceId(String furnaceId);

    List<SpcBaseline> findByFurnaceIdAndOperationMode(String furnaceId, String operationMode);

    List<SpcBaseline> findByParamName(String paramName);

    /**
     * 從 furnace_metrics 計算某爐某參數的統計（mean、stddev、count）
     * dynamicColumn 是 param_name 對應的 DB 欄位名（heater_temp / diameter 等）
     */
    @Query(value = "SELECT " +
            "  AVG(:#{#dynColumn}) AS mean_val, " +
            "  STDDEV(:#{#dynColumn}) AS std_val, " +
            "  COUNT(*) AS cnt " +
            "FROM furnace_metrics " +
            "WHERE furnace_id = :furnaceId " +
            "  AND ts >= NOW() - INTERVAL '7 days' " +
            "  AND :#{#dynColumn} IS NOT NULL",
            nativeQuery = true)
    Object[] rawStats(@Param("furnaceId") String furnaceId, @Param("dynColumn") String dynColumn);
}
