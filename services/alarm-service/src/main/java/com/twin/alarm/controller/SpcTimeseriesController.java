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

@RestController
@RequestMapping("/spc")
@RequiredArgsConstructor
public class SpcTimeseriesController {

    private static final Logger log = LoggerFactory.getLogger(SpcTimeseriesController.class);

    @PersistenceContext
    private EntityManager em;

    @GetMapping("/timeseries")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public List<Map<String, Object>> timeseries(
            @RequestParam String furnaceId,
            @RequestParam String paramName,
            @RequestParam(defaultValue = "60") int minutes) {

        String column = SpcBaselineService.PARAM_COLUMN.get(paramName);
        if (column == null) {
            throw new IllegalArgumentException("Unknown paramName: " + paramName);
        }

        String sql = String.format(
                "SELECT " +
                        "  time_bucket('30 seconds', time) AS ts, " +
                        "  AVG(%s) AS value " +
                        "FROM furnace_metrics " +
                        "WHERE furnace_id = ?1 " +
                        "  AND time >= (SELECT MAX(time) FROM furnace_metrics WHERE furnace_id = ?1) - INTERVAL '%d minutes' " +
                        "  AND %s IS NOT NULL " +
                        "GROUP BY ts " +
                        "ORDER BY ts ASC " +
                        "LIMIT 200",
                column, minutes, column);

        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter(1, furnaceId)
                .getResultList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> point = new HashMap<>();
            point.put("ts", row[0]);
            point.put("value", row[1] == null ? null : ((Number) row[1]).doubleValue());
            result.add(point);
        }
        return result;
    }
}
