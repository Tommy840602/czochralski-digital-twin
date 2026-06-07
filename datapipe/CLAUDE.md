# Python Datapipe — 子模組說明

- Python 3.11，依賴：paho-mqtt, asyncua, pandas, pydantic, PyYAML
- `main.py --furnace C1 --csv data/C2_growthIdx_1.csv` 啟動
- 每 10 秒讀 CSV 一行，模擬 OPC-UA + MQTT 發送
- Event=1 → topic `furnace/C1`，Event=6 → topic `furnace/C2`
- CSV 跑完（Event=6）→ 發 alarm payload 到 `furnace/alarm`，然後停止
- JSON payload 格式見 @docs/data-schema.md
- MQTT QoS=1，broker=localhost:1883
