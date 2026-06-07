package com.twin.flink.sink;

import com.twin.flink.model.FurnaceReading;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.api.connector.sink2.WriterInitContext;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class TimescaleDbSink implements Sink<FurnaceReading> {

    private static final String URL  = System.getenv().getOrDefault("TIMESCALE_URL",
        "jdbc:postgresql://twin-timescaledb:5432/furnace_db");
    private static final String USER = System.getenv().getOrDefault("TIMESCALE_USER", "twin");
    private static final String PASS = System.getenv().getOrDefault("TIMESCALE_PASSWORD", "twin_secret");

    @Override
    public SinkWriter<FurnaceReading> createWriter(WriterInitContext ctx) throws IOException {
        return new TimescaleWriter(URL, USER, PASS);
    }

    static class TimescaleWriter implements SinkWriter<FurnaceReading> {

        private static final String SQL =
            "INSERT INTO furnace_metrics " +
            "(time, ingot_no, furnace_id, operation_mode, event, " +
            " diameter, diameter_target, heater_temp, heater_power_sv, " +
            " gr_mean, body_length, residual_weight) " +
            "VALUES (NOW(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT DO NOTHING";

        private Connection conn;
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
                ps.setString(1,  r.getIngotNo());
                ps.setString(2,  r.getFurnaceId());
                ps.setString(3,  r.getOperationMode());
                ps.setInt(4,     r.getEvent() != null ? r.getEvent() : 1);
                ps.setObject(5,  r.getDiameter());
                ps.setObject(6,  r.getDiameterTarget());
                ps.setObject(7,  r.getHeaterTemp());
                ps.setObject(8,  r.getHeaterPowerSv());
                ps.setObject(9,  r.getGrMean());
                ps.setObject(10, r.getBodyLength());
                ps.setObject(11, r.getResidualWeight());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IOException("TimescaleDB 寫入失敗: " + e.getMessage(), e);
            }
        }

        @Override public void flush(boolean endOfInput) throws IOException {}

        @Override
        public void close() throws Exception {
            if (ps   != null) ps.close();
            if (conn != null) conn.close();
        }
    }
}
