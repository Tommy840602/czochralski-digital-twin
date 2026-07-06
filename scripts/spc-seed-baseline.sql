-- SPC Baseline Seed: 從 furnace_metrics 8000 萬筆歷史算 baseline

INSERT INTO spc_baseline (furnace_id, param_name, mean, std_dev, ucl_3sigma, lcl_3sigma, ucl_2sigma, lcl_2sigma, ucl_1sigma, lcl_1sigma, sample_size, calculated_at)
SELECT furnace_id, 'heaterTemp',
       AVG(heater_temp), STDDEV(heater_temp),
       AVG(heater_temp) + 3 * STDDEV(heater_temp), AVG(heater_temp) - 3 * STDDEV(heater_temp),
       AVG(heater_temp) + 2 * STDDEV(heater_temp), AVG(heater_temp) - 2 * STDDEV(heater_temp),
       AVG(heater_temp) + STDDEV(heater_temp), AVG(heater_temp) - STDDEV(heater_temp),
       COUNT(*)::int, NOW()
FROM furnace_metrics WHERE time >= NOW() - INTERVAL '7 days' AND heater_temp IS NOT NULL
GROUP BY furnace_id
ON CONFLICT (furnace_id, param_name) DO NOTHING;

INSERT INTO spc_baseline (furnace_id, param_name, mean, std_dev, ucl_3sigma, lcl_3sigma, ucl_2sigma, lcl_2sigma, ucl_1sigma, lcl_1sigma, sample_size, calculated_at)
SELECT furnace_id, 'diameter',
       AVG(diameter), STDDEV(diameter),
       AVG(diameter) + 3 * STDDEV(diameter), AVG(diameter) - 3 * STDDEV(diameter),
       AVG(diameter) + 2 * STDDEV(diameter), AVG(diameter) - 2 * STDDEV(diameter),
       AVG(diameter) + STDDEV(diameter), AVG(diameter) - STDDEV(diameter),
       COUNT(*)::int, NOW()
FROM furnace_metrics WHERE time >= NOW() - INTERVAL '7 days' AND diameter IS NOT NULL
GROUP BY furnace_id
ON CONFLICT (furnace_id, param_name) DO NOTHING;

INSERT INTO spc_baseline (furnace_id, param_name, mean, std_dev, ucl_3sigma, lcl_3sigma, ucl_2sigma, lcl_2sigma, ucl_1sigma, lcl_1sigma, sample_size, calculated_at)
SELECT furnace_id, 'grMean',
       AVG(gr_mean), STDDEV(gr_mean),
       AVG(gr_mean) + 3 * STDDEV(gr_mean), AVG(gr_mean) - 3 * STDDEV(gr_mean),
       AVG(gr_mean) + 2 * STDDEV(gr_mean), AVG(gr_mean) - 2 * STDDEV(gr_mean),
       AVG(gr_mean) + STDDEV(gr_mean), AVG(gr_mean) - STDDEV(gr_mean),
       COUNT(*)::int, NOW()
FROM furnace_metrics WHERE time >= NOW() - INTERVAL '7 days' AND gr_mean IS NOT NULL
GROUP BY furnace_id
ON CONFLICT (furnace_id, param_name) DO NOTHING;

INSERT INTO spc_baseline (furnace_id, param_name, mean, std_dev, ucl_3sigma, lcl_3sigma, ucl_2sigma, lcl_2sigma, ucl_1sigma, lcl_1sigma, sample_size, calculated_at)
SELECT furnace_id, 'heaterPowerSv',
       AVG(heater_power_sv), STDDEV(heater_power_sv),
       AVG(heater_power_sv) + 3 * STDDEV(heater_power_sv), AVG(heater_power_sv) - 3 * STDDEV(heater_power_sv),
       AVG(heater_power_sv) + 2 * STDDEV(heater_power_sv), AVG(heater_power_sv) - 2 * STDDEV(heater_power_sv),
       AVG(heater_power_sv) + STDDEV(heater_power_sv), AVG(heater_power_sv) - STDDEV(heater_power_sv),
       COUNT(*)::int, NOW()
FROM furnace_metrics WHERE time >= NOW() - INTERVAL '7 days' AND heater_power_sv IS NOT NULL
GROUP BY furnace_id
ON CONFLICT (furnace_id, param_name) DO NOTHING;

INSERT INTO spc_baseline (furnace_id, param_name, mean, std_dev, ucl_3sigma, lcl_3sigma, ucl_2sigma, lcl_2sigma, ucl_1sigma, lcl_1sigma, sample_size, calculated_at)
SELECT furnace_id, 'seedLift',
       AVG(seed_lift), STDDEV(seed_lift),
       AVG(seed_lift) + 3 * STDDEV(seed_lift), AVG(seed_lift) - 3 * STDDEV(seed_lift),
       AVG(seed_lift) + 2 * STDDEV(seed_lift), AVG(seed_lift) - 2 * STDDEV(seed_lift),
       AVG(seed_lift) + STDDEV(seed_lift), AVG(seed_lift) - STDDEV(seed_lift),
       COUNT(*)::int, NOW()
FROM furnace_metrics WHERE time >= NOW() - INTERVAL '7 days' AND seed_lift IS NOT NULL
GROUP BY furnace_id
ON CONFLICT (furnace_id, param_name) DO NOTHING;

INSERT INTO spc_baseline (furnace_id, param_name, mean, std_dev, ucl_3sigma, lcl_3sigma, ucl_2sigma, lcl_2sigma, ucl_1sigma, lcl_1sigma, sample_size, calculated_at)
SELECT furnace_id, 'bodyLength',
       AVG(body_length), STDDEV(body_length),
       AVG(body_length) + 3 * STDDEV(body_length), AVG(body_length) - 3 * STDDEV(body_length),
       AVG(body_length) + 2 * STDDEV(body_length), AVG(body_length) - 2 * STDDEV(body_length),
       AVG(body_length) + STDDEV(body_length), AVG(body_length) - STDDEV(body_length),
       COUNT(*)::int, NOW()
FROM furnace_metrics WHERE time >= NOW() - INTERVAL '7 days' AND body_length IS NOT NULL
GROUP BY furnace_id
ON CONFLICT (furnace_id, param_name) DO NOTHING;

SELECT COUNT(*) AS baseline_count FROM spc_baseline;