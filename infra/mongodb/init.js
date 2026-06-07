// ============================================================
//  MongoDB 初始化 — 感測器數據 + Alarm 訊息
//  Czochralski Digital Twin
// ============================================================

db = db.getSiblingDB('twin_db');

// ─────────────────────────────────────────
// sensor_data collection
// ─────────────────────────────────────────
db.createCollection('sensor_data', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['log_time', 'furnace_id', 'ingot_no', 'event'],
            properties: {
                log_time:       { bsonType: 'date' },
                furnace_id:     { bsonType: 'string' },
                ingot_no:       { bsonType: 'string' },
                operation_mode: { bsonType: 'string' },
                event:          { bsonType: 'int' },
                // 所有 CSV 欄位以 raw_data 子文件儲存
                raw_data:       { bsonType: 'object' },
                received_at:    { bsonType: 'date' },
                kafka_offset:   { bsonType: 'long' },
                kafka_partition:{ bsonType: 'int' }
            }
        }
    },
    validationAction: 'warn'
});

// TTL Index：sensor_data 保留 30 天
db.sensor_data.createIndex(
    { received_at: 1 },
    { expireAfterSeconds: 2592000, name: 'ttl_30d' }
);

// 查詢索引
db.sensor_data.createIndex({ furnace_id: 1, log_time: -1 }, { name: 'idx_furnace_time' });
db.sensor_data.createIndex({ ingot_no: 1, log_time: -1 },   { name: 'idx_ingot_time' });
db.sensor_data.createIndex({ event: 1, log_time: -1 },      { name: 'idx_event_time' });

// ─────────────────────────────────────────
// alarm_messages collection
// ─────────────────────────────────────────
db.createCollection('alarm_messages', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['alarm_type', 'furnace_id', 'ingot_no', 'severity', 'triggered_at'],
            properties: {
                alarm_type:    { bsonType: 'string' },  // DiameterDrift / HeaterOverheat / NgDisconnect / CsvEnd
                furnace_id:    { bsonType: 'string' },
                ingot_no:      { bsonType: 'string' },
                severity:      { enum: ['INFO', 'WARN', 'CRITICAL'] },
                message:       { bsonType: 'string' },
                triggered_at:  { bsonType: 'date' },
                resolved_at:   { bsonType: ['date', 'null'] },
                is_resolved:   { bsonType: 'bool' },
                slack_sent:    { bsonType: 'bool' },
                slack_ts:      { bsonType: ['string', 'null'] },   // Slack message timestamp
                context: {
                    bsonType: 'object',
                    properties: {
                        diameter:       { bsonType: ['double', 'null'] },
                        diameter_target:{ bsonType: ['double', 'null'] },
                        heater_temp:    { bsonType: ['double', 'null'] },
                        event:          { bsonType: ['int', 'null'] },
                        operation_mode: { bsonType: ['string', 'null'] }
                    }
                }
            }
        }
    }
});

// Alarm 查詢索引
db.alarm_messages.createIndex({ furnace_id: 1, triggered_at: -1 }, { name: 'idx_alarm_furnace' });
db.alarm_messages.createIndex({ alarm_type: 1, triggered_at: -1 }, { name: 'idx_alarm_type' });
db.alarm_messages.createIndex({ is_resolved: 1, severity: 1 },     { name: 'idx_alarm_unresolved' });
db.alarm_messages.createIndex({ triggered_at: -1 },                { name: 'idx_alarm_time' });

// TTL：alarm_messages 保留 365 天
db.alarm_messages.createIndex(
    { triggered_at: 1 },
    { expireAfterSeconds: 31536000, name: 'ttl_365d' }
);

// ─────────────────────────────────────────
// flink_state collection (Flink checkpoint 輔助)
// ─────────────────────────────────────────
db.createCollection('flink_state');
db.flink_state.createIndex({ furnace_id: 1 }, { unique: true });

// ─────────────────────────────────────────
// 建立應用帳號
// ─────────────────────────────────────────
db.createUser({
    user: 'twin_app',
    pwd: 'twin_app_secret',
    roles: [
        { role: 'readWrite', db: 'twin_db' }
    ]
});

// ─────────────────────────────────────────
// 種子資料：初始 Alarm 範例
// ─────────────────────────────────────────
db.alarm_messages.insertOne({
    alarm_type:   'CsvEnd',
    furnace_id:   'C2',
    ingot_no:     'C226654E',
    severity:     'CRITICAL',
    message:      '[長晶爐 C2] NG CSV 播完，連線中斷 (Event=6)',
    triggered_at: new Date('2026-06-03T03:55:14Z'),
    resolved_at:  null,
    is_resolved:  false,
    slack_sent:   false,
    slack_ts:     null,
    context: {
        diameter:        10.03,
        diameter_target: 13.08,
        heater_temp:     1295.0,
        event:           6,
        operation_mode:  'NECK4'
    }
});

print('✅ MongoDB 初始化完成');
