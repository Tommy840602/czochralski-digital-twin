package com.twin.alarm.spc;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Whitelist for SPC queryable metrics.
 *
 * Keep this enum as the single source of truth for mapping public API paramName
 * values to physical database columns. Do not concatenate arbitrary request
 * parameters into SQL.
 */
public enum SpcParam {
    HEATER_TEMP("heaterTemp", "heater_temp", "Heater Temp", "°C"),
    DIAMETER("diameter", "diameter", "Diameter", "mm"),
    GR_MEAN("grMean", "gr_mean", "Growth Rate", "mm/m"),
    HEATER_POWER_SV("heaterPowerSv", "heater_power_sv", "Heater Power", "kW"),
    SEED_LIFT("seedLift", "seed_lift", "Seed Lift", "mm"),
    BODY_LENGTH("bodyLength", "body_length", "Body Length", "mm");

    private final String apiName;
    private final String columnName;
    private final String label;
    private final String unit;

    SpcParam(String apiName, String columnName, String label, String unit) {
        this.apiName = apiName;
        this.columnName = columnName;
        this.label = label;
        this.unit = unit;
    }

    public String apiName() {
        return apiName;
    }

    public String columnName() {
        return columnName;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }

    public static List<SpcParam> all() {
        return Arrays.asList(values());
    }

    public static Optional<SpcParam> fromApiName(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(param -> param.apiName.toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }
}
