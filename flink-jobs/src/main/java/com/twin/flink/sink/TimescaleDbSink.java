package com.twin.flink.sink;

import com.twin.flink.model.FurnaceReading;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.api.connector.sink2.WriterInitContext;

import java.io.IOException;
import java.sql.*;

/**
 * TimescaleDbSink — 寫入 furnace_metrics（彈性多爐，全 44 欄）
 * furnace_id 直接從 FurnaceReading.furnaceId 取得，無需硬編碼。
 */
public class TimescaleDbSink implements Sink<FurnaceReading> {

    private static final String URL  = System.getenv().getOrDefault(
            "TIMESCALE_URL",  "jdbc:postgresql://twin-timescaledb:5432/furnace_db");
    private static final String USER = System.getenv().getOrDefault("TIMESCALE_USER", "twin");
    private static final String PASS = System.getenv().getOrDefault("TIMESCALE_PASSWORD", "twin_secret");

    @Override
    public SinkWriter<FurnaceReading> createWriter(WriterInitContext ctx) throws IOException {
        return new TimescaleWriter(URL, USER, PASS);
    }

    static class TimescaleWriter implements SinkWriter<FurnaceReading> {

        // 44 個感測器欄位全部寫入
        private static final String SQL = """
            INSERT INTO furnace_metrics (
                time, furnace_id, ingot_no, operation_mode, sop,
                diameter, d_mean, diameter_target,
                heater_temp, heater_temp_target, heater_power_sv, ht_mean,
                temp2, temp4, temp5, temp9, temp29,
                gr_mean, body_length, neck_length_accum,
                seed_lift, seed_lift_sp, seed_lift_target, seed_rotation_sp,
                crucible_rotation_sp, cr_mean, crucible_lift, crucible_lift_ratio,
                crucible_position, crucible_pos_calibrated, ctpfl_pul,
                magnet_pv,
                argon_flow_rate, lower_chamber_press, lower_chamber_press_sp,
                thro_valve_open, bp_mean, bpu60mean, btpl_bpul1, btpl_bpll1,
                pidsl_ddmean, pidsl_temp1,
                residual_weight, countb
            ) VALUES (
                NOW(), ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?,
                ?,
                ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?,
                ?, ?
            ) ON CONFLICT DO NOTHING
            """;

        private Connection       conn;
        private PreparedStatement ps;

        TimescaleWriter(String url, String user, String pass) throws IOException {
            try {
                Class.forName("org.postgresql.Driver");
                conn = DriverManager.getConnection(url, user, pass);
                conn.setAutoCommit(true);
                ps = conn.prepareStatement(SQL);
            } catch (Exception e) {
                throw new IOException("TimescaleDB 連線失敗: " + e.getMessage(), e);
            }
        }

        @Override
        public void write(FurnaceReading r, Context ctx) throws IOException {
            try {
                int i = 1;
                ps.setString(i++, r.getFurnaceId());
                ps.setString(i++, r.getIngotNo());
                ps.setString(i++, r.getOperationMode());
                ps.setString(i++, r.getSop());

                // 直徑
                setDbl(i++, r.getDiameter());
                setDbl(i++, r.getDMean());
                setDbl(i++, r.getDiameterTarget());

                // 溫度
                setDbl(i++, r.getHeaterTemp());
                setDbl(i++, r.getHeaterTempTarget());
                setDbl(i++, r.getHeaterPowerSv());
                setDbl(i++, r.getHtMean());
                setDbl(i++, r.getTemp2());
                setDbl(i++, r.getTemp4());
                setDbl(i++, r.getTemp5());
                setDbl(i++, r.getTemp9());
                setDbl(i++, r.getTemp29());

                // 生長
                setDbl(i++, r.getGrMean());
                setDbl(i++, r.getBodyLength());
                setDbl(i++, r.getNeckLengthAccum());

                // 晶種
                setDbl(i++, r.getSeedLift());
                setDbl(i++, r.getSeedLiftSp());
                setDbl(i++, r.getSeedLiftTarget());
                setDbl(i++, r.getSeedRotationSp());

                // 坩堝
                setDbl(i++, r.getCrucibleRotationSp());
                setDbl(i++, r.getCrMean());
                setDbl(i++, r.getCrucibleLift());
                setDbl(i++, r.getCrucibleLiftRatio());
                setDbl(i++, r.getCruciblePosition());
                setDbl(i++, r.getCruciblePosCalibrated());
                setDbl(i++, r.getCtpflPul());

                // 磁場
                setDbl(i++, r.getMagnetPv());

                // 壓力
                setDbl(i++, r.getArgonFlowRate());
                setDbl(i++, r.getLowerChamberPress());
                setDbl(i++, r.getLowerChamberPressSp());
                setDbl(i++, r.getThroValveOpen());
                setDbl(i++, r.getBpMean());
                setDbl(i++, r.getBpu60mean());
                setDbl(i++, r.getBtplBpul1());
                setDbl(i++, r.getBtplBpll1());

                // PID
                setDbl(i++, r.getPidslDdmean());
                setDbl(i++, r.getPidslTemp1());

                // 其他
                setDbl(i++, r.getResidualWeight());
                if (r.getCountb() != null) ps.setInt(i, r.getCountb());
                else ps.setNull(i, Types.INTEGER);

                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IOException("TimescaleDB 寫入失敗: " + e.getMessage(), e);
            }
        }

        private void setDbl(int idx, Double val) throws SQLException {
            if (val != null) ps.setDouble(idx, val);
            else             ps.setNull(idx, Types.DOUBLE);
        }

        @Override public void flush(boolean endOfInput) {}
        @Override public void close() throws Exception {
            if (ps   != null) ps.close();
            if (conn != null) conn.close();
        }
    }
}