package com.twin.alarm.spc;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SpcService {

    private static final int BASELINE_HOURS = 24;
    private static final int MAX_TIMESERIES_POINTS = 2_000;
    private static final int MAX_VIOLATION_ROWS_PER_PARAM = 300;

    private final NamedParameterJdbcTemplate jdbc;

    public SpcService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<BaselineDto> getBaselines(String furnaceId) {
        validateFurnaceId(furnaceId);
        List<BaselineDto> result = new ArrayList<>();
        for (SpcParam param : SpcParam.all()) {
            getBaseline(furnaceId, param.apiName()).ifPresent(result::add);
        }
        return result;
    }

    public java.util.Optional<BaselineDto> getBaseline(String furnaceId, String paramName) {
        validateFurnaceId(furnaceId);
        SpcParam param = requireParam(paramName);
        Instant since = Instant.now().minus(BASELINE_HOURS, ChronoUnit.HOURS);

        String sql = """
                SELECT
                    COUNT(%1$s) AS sample_count,
                    AVG(%1$s) AS mean,
                    STDDEV_SAMP(%1$s) AS std_dev
                FROM furnace_metrics
                WHERE furnace_id = :furnaceId
                  AND time >= :since
                  AND %1$s IS NOT NULL
                """.formatted(param.columnName());

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("furnaceId", furnaceId)
                .addValue("since", Timestamp.from(since));

        return jdbc.query(sql, params, rs -> {
            if (!rs.next()) {
                return java.util.Optional.empty();
            }
            long count = rs.getLong("sample_count");
            if (count <= 0) {
                return java.util.Optional.empty();
            }
            double mean = rs.getDouble("mean");
            double stdDev = rs.getObject("std_dev") == null ? 0.0 : rs.getDouble("std_dev");
            return java.util.Optional.of(toBaseline(furnaceId, param, count, mean, stdDev));
        });
    }

    public List<ParamDto> getParams() {
        return SpcParam.all().stream()
                .map(p -> new ParamDto(p.apiName(), p.label(), p.unit()))
                .toList();
    }

    public List<ViolationDto> getRecentViolations(int minutes) {
        int safeMinutes = clampMinutes(minutes);
        Instant since = Instant.now().minus(safeMinutes, ChronoUnit.MINUTES);
        Instant baselineSince = Instant.now().minus(BASELINE_HOURS, ChronoUnit.HOURS);

        List<ViolationDto> result = new ArrayList<>();
        for (SpcParam param : SpcParam.all()) {
            result.addAll(findRule1Violations(param, null, since, baselineSince));
        }
        result.sort(Comparator.comparing(ViolationDto::ts).reversed());
        return result.stream().limit(500).toList();
    }

    public List<ViolationDto> getViolationsByFurnace(String furnaceId, int minutes) {
        validateFurnaceId(furnaceId);
        int safeMinutes = clampMinutes(minutes);
        Instant since = Instant.now().minus(safeMinutes, ChronoUnit.MINUTES);
        Instant baselineSince = Instant.now().minus(BASELINE_HOURS, ChronoUnit.HOURS);

        List<ViolationDto> result = new ArrayList<>();
        for (SpcParam param : SpcParam.all()) {
            result.addAll(findRule1Violations(param, furnaceId, since, baselineSince));
        }
        result.sort(Comparator.comparing(ViolationDto::ts).reversed());
        return result.stream().limit(500).toList();
    }

    public Map<Integer, Long> getStatistics(int minutes) {
        Map<Integer, Long> stats = new LinkedHashMap<>();
        for (int i = 1; i <= 8; i++) {
            stats.put(i, 0L);
        }
        stats.put(1, (long) getRecentViolations(minutes).size());
        return stats;
    }

    public List<TimeseriesPointDto> getTimeseries(String furnaceId, String paramName, int minutes) {
        validateFurnaceId(furnaceId);
        SpcParam param = requireParam(paramName);
        int safeMinutes = clampMinutes(minutes);
        Instant since = Instant.now().minus(safeMinutes, ChronoUnit.MINUTES);

        String sql = """
                SELECT time AS ts, %1$s AS value
                FROM furnace_metrics
                WHERE furnace_id = :furnaceId
                  AND time >= :since
                  AND %1$s IS NOT NULL
                ORDER BY time ASC
                LIMIT :limit
                """.formatted(param.columnName());

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("furnaceId", furnaceId)
                .addValue("since", Timestamp.from(since))
                .addValue("limit", MAX_TIMESERIES_POINTS);

        return jdbc.query(sql, params, (rs, rowNum) -> new TimeseriesPointDto(
                rs.getTimestamp("ts").toInstant(),
                rs.getDouble("value")
        ));
    }

    public Map<String, Object> rebuildBaseline() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("message", "SPC baseline is calculated on demand from the latest " + BASELINE_HOURS + " hours of furnace_metrics.");
        response.put("baselineHours", BASELINE_HOURS);
        response.put("rebuiltAt", Instant.now().toString());
        return response;
    }

    private List<ViolationDto> findRule1Violations(SpcParam param, String furnaceId, Instant since, Instant baselineSince) {
        String furnacePredicate = StringUtils.hasText(furnaceId) ? "AND m.furnace_id = :furnaceId" : "";
        String sql = """
                WITH baseline AS (
                    SELECT
                        furnace_id,
                        COUNT(%1$s) AS sample_count,
                        AVG(%1$s) AS mean,
                        STDDEV_SAMP(%1$s) AS std_dev
                    FROM furnace_metrics
                    WHERE time >= :baselineSince
                      AND %1$s IS NOT NULL
                    GROUP BY furnace_id
                    HAVING COUNT(%1$s) >= 2 AND STDDEV_SAMP(%1$s) > 0
                )
                SELECT
                    m.time AS ts,
                    m.furnace_id,
                    m.%1$s AS value,
                    b.mean,
                    b.std_dev
                FROM furnace_metrics m
                JOIN baseline b ON b.furnace_id = m.furnace_id
                WHERE m.time >= :since
                  AND m.%1$s IS NOT NULL
                  %2$s
                  AND (m.%1$s > b.mean + 3 * b.std_dev OR m.%1$s < b.mean - 3 * b.std_dev)
                ORDER BY m.time DESC
                LIMIT :limit
                """.formatted(param.columnName(), furnacePredicate);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("since", Timestamp.from(since))
                .addValue("baselineSince", Timestamp.from(baselineSince))
                .addValue("limit", MAX_VIOLATION_ROWS_PER_PARAM);
        if (StringUtils.hasText(furnaceId)) {
            params.addValue("furnaceId", furnaceId);
        }

        return jdbc.query(sql, params, (rs, rowNum) -> {
            double mean = rs.getDouble("mean");
            double stdDev = rs.getDouble("std_dev");
            double value = rs.getDouble("value");
            String rowFurnaceId = rs.getString("furnace_id");
            BaselineDto baseline = toBaseline(rowFurnaceId, param, 0L, mean, stdDev);
            String id = rowFurnaceId + ":" + param.apiName() + ":" + rs.getTimestamp("ts").toInstant();
            return new ViolationDto(
                    id,
                    rs.getTimestamp("ts").toInstant(),
                    rowFurnaceId,
                    param.apiName(),
                    1,
                    "1 point outside 3σ",
                    value,
                    baseline.mean(),
                    baseline.stdDev(),
                    baseline.ucl1sigma(),
                    baseline.lcl1sigma(),
                    baseline.ucl2sigma(),
                    baseline.lcl2sigma(),
                    baseline.ucl3sigma(),
                    baseline.lcl3sigma(),
                    "CRITICAL"
            );
        });
    }

    private BaselineDto toBaseline(String furnaceId, SpcParam param, long sampleCount, double mean, double stdDev) {
        return new BaselineDto(
                furnaceId + ":" + param.apiName(),
                furnaceId,
                param.apiName(),
                sampleCount,
                mean,
                stdDev,
                mean + stdDev,
                mean - stdDev,
                mean + 2 * stdDev,
                mean - 2 * stdDev,
                mean + 3 * stdDev,
                mean - 3 * stdDev
        );
    }

    private SpcParam requireParam(String paramName) {
        return SpcParam.fromApiName(paramName)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported SPC paramName: " + paramName));
    }

    private void validateFurnaceId(String furnaceId) {
        if (!StringUtils.hasText(furnaceId) || !furnaceId.matches("[A-Za-z0-9_-]{1,20}")) {
            throw new IllegalArgumentException("Invalid furnaceId: " + furnaceId);
        }
    }

    private int clampMinutes(int minutes) {
        if (minutes <= 0) {
            return 60;
        }
        return Math.min(minutes, 24 * 60);
    }

    public record BaselineDto(
            String id,
            String furnaceId,
            String paramName,
            long sampleCount,
            double mean,
            double stdDev,
            double ucl1sigma,
            double lcl1sigma,
            double ucl2sigma,
            double lcl2sigma,
            double ucl3sigma,
            double lcl3sigma
    ) {
    }

    public record ParamDto(String key, String label, String unit) {
    }

    public record TimeseriesPointDto(Instant ts, double value) {
    }

    public record ViolationDto(
            String id,
            Instant ts,
            String furnaceId,
            String paramName,
            int ruleId,
            String ruleName,
            double value,
            double mean,
            double stdDev,
            double ucl1sigma,
            double lcl1sigma,
            double ucl2sigma,
            double lcl2sigma,
            double ucl3sigma,
            double lcl3sigma,
            String severity
    ) {
    }
}
