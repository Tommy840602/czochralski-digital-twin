#!/bin/bash
echo "=== 啟動長晶爐數位孿生系統 ==="

# Step 1: Docker 基礎設施
echo "[1/3] 啟動 Docker 服務..."
cd ~/czochralski-digital-twin
./scripts/start.sh up
echo "Docker 服務已就緒"

echo ""
echo "=== 請手動完成以下步驟 ==="
echo "[2/3] IntelliJ 依序啟動以下服務："
echo "  ① EurekaServerApplication     :8761"
echo "  ② ConfigServerApplication     :8888"
echo "  ③ IotGatewayApplication        :8090"
echo "  ④ FurnaceServiceApplication    :8091"
echo "  ⑤ AlarmServiceApplication      :8092"
echo "  ⑥ TwinStateServiceApplication  :8093"
echo "  ⑦ ApiGatewayApplication        :8085"
echo ""
echo "[3/3] 前端 & 模擬器："
echo "  cd ~/czochralski-digital-twin/frontend && npm run dev"
echo "  cd ~/czochralski-digital-twin/datapipe && python main.py --furnace C1 --csv data/C2_growthIdx_1.csv"
echo "  cd ~/czochralski-digital-twin/datapipe && python main.py --furnace C2 --csv data/C2_growthIdx_5.csv"
echo ""
echo "=== 服務入口 ==="
echo "  前端:      http://localhost:5173"
echo "  Grafana:   http://localhost:3000  (admin/twin_admin)"
echo "  Kafka UI:  http://localhost:8080"
echo "  Flink UI:  http://localhost:8083"
echo "  Eureka:    http://localhost:8761"
