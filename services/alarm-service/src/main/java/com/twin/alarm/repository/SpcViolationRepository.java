package com.twin.alarm.repository;

import com.twin.alarm.entity.SpcViolation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

public interface SpcViolationRepository extends JpaRepository<SpcViolation, SpcViolation.PK> {

    @Query("SELECT v FROM SpcViolation v WHERE v.ts >= :since ORDER BY v.ts DESC")
    List<SpcViolation> findRecent(@Param("since") Instant since);

    @Query("SELECT v FROM SpcViolation v WHERE v.furnaceId = :furnaceId AND v.ts >= :since ORDER BY v.ts DESC")
    List<SpcViolation> findByFurnace(@Param("furnaceId") String furnaceId, @Param("since") Instant since);

    @Query("SELECT v FROM SpcViolation v WHERE v.furnaceId = :furnaceId AND v.paramName = :paramName AND v.ts >= :since ORDER BY v.ts DESC")
    List<SpcViolation> findByFurnaceParam(@Param("furnaceId") String furnaceId,
                                          @Param("paramName") String paramName,
                                          @Param("since") Instant since);

    @Query("SELECT v.ruleId, COUNT(v) FROM SpcViolation v WHERE v.ts >= :since GROUP BY v.ruleId")
    List<Object[]> countByRuleSince(@Param("since") Instant since);

    @Query("SELECT v.ruleId, COUNT(v) FROM SpcViolation v WHERE v.ts >= :since AND v.furnaceId = :furnaceId GROUP BY v.ruleId")
    List<Object[]> countByRuleSinceAndFurnace(@Param("since") Instant since, @Param("furnaceId") String furnaceId);

    @Query("SELECT v.ruleId, COUNT(v) FROM SpcViolation v WHERE v.ts >= :since AND v.furnaceId = :furnaceId AND v.paramName = :paramName GROUP BY v.ruleId")
    List<Object[]> countByRuleSinceAndFurnaceAndParam(@Param("since") Instant since,
                                                      @Param("furnaceId") String furnaceId,
                                                      @Param("paramName") String paramName);

    @Modifying
    @Transactional
    @Query("DELETE FROM SpcViolation v WHERE v.furnaceId = :furnaceId AND v.paramName = :paramName")
    void deleteByFurnaceIdAndParamName(@Param("furnaceId") String furnaceId, @Param("paramName") String paramName);
}
