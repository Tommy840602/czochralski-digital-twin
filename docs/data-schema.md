# 數據 Schema 速查

## CSV 欄位（C2_growthIdx_1.csv / _5.csv）

| 欄位 | 類型 | 說明 | 範例值 |
|---|---|---|---|
| LogTime | datetime | 記錄時間 | 2026-06-03 00:14:25 |
| INGOT_NO | string | 錠號（Kafka Partition Key）| C225C34E |
| Event | int | 1=OK, 6=NG | 1 |
| Operation Mode | string | 作業模式 | NECK4 / BODY |
| SOP | string | 標準作業程序 | |
| Diameter | float | 直徑 mm | 15.14 |
| D_mean | float | 直徑平均 | |
| Diameter target | float | 目標直徑 | 13.08 |
| Heater temp | float | 加熱器溫度 °C | 1301 |
| Heater temp target | float | 目標溫度 | |
| Heater Power SV | float | 加熱功率 kW | 57.6 |
| HT_mean | float | 溫度平均 | |
| GR_mean | float | 生長速率 mm/min | 1.12 |
| Seed lift | float | 晶種提升 | |
| Seed lift SP | float | 提升設定點 | |
| Seed lift target | float | 提升目標 | |
| Body length | float | 晶棒長度 mm | |
| Neck length Accum | float | 頸部累積長度 | |
| Magnet PV | float | 磁場 | |
| Temp2/4/5/29 | float | 各點溫度 | |
| Crucible Rotation SP | float | 坩堝轉速 | |
| CR_mean | float | 坩堝轉速平均 | |
| Residual Weight | float | 殘餘重量 kg | |
| BP_mean | float | | |
| BPU60mean | float | | |
| PIDSL_DDmean | float | | |
| PIDSL_Temp1 | float | | |

## Kafka Message Payload (JSON)
```json
{
  "ingotNo": "C225C34E",
  "furnaceId": "C1",
  "logTime": "2026-06-03T00:14:25",
  "event": 1,
  "operationMode": "NECK4",
  "diameter": 15.14,
  "diameterTarget": 13.08,
  "heaterTemp": 1301.0,
  "heaterPowerSv": 57.6,
  "grMean": 1.12,
  "bodyLength": 0.0,
  "receivedAt": "2026-06-03T00:14:25.123Z"
}
```

## Redis Key 結構
```
furnace:C1  →  HASH { diameter, heaterTemp, grMean, event, updatedAt }
furnace:C2  →  HASH { ... }
alarm-channel  →  Pub/Sub channel
```

## MongoDB alarm_messages document
```json
{
  "alarmType": "NgDisconnect",
  "furnaceId": "C2",
  "ingotNo": "C226654E",
  "severity": "CRITICAL",
  "message": "[長晶爐 C2] NG CSV 播完，連線中斷",
  "triggeredAt": "2026-06-03T03:55:14Z",
  "isResolved": false,
  "slackSent": false,
  "context": {
    "diameter": 10.03,
    "diameterTarget": 13.08,
    "heaterTemp": 1295.0,
    "event": 6
  }
}
```
