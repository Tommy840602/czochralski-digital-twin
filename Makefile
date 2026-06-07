# ============================================================
#  長晶爐數位孿生 — Makefile
# ============================================================
.PHONY: help infra-up infra-down infra-status \
        build-all build-gateway build-flink build-frontend \
        sim-ok sim-ng test clean

help:
	@echo ""
	@echo "  長晶爐數位孿生 — 指令清單"
	@echo "  ─────────────────────────────────────────"
	@echo "  make infra-up        啟動所有基礎設施"
	@echo "  make infra-down      停止基礎設施"
	@echo "  make infra-status    查看服務狀態"
	@echo "  make sim-ok          模擬 OK 爐 (Event=1)"
	@echo "  make sim-ng          模擬 NG 爐 (Event=6)"
	@echo "  make build-all       Build 所有服務"
	@echo "  make build-flink     Build Flink Job JAR"
	@echo "  make build-frontend  Build Three.js 前端"
	@echo "  make test            執行所有測試"
	@echo "  make clean           清理 build artifacts"
	@echo ""

# ── 基礎設施 ──
infra-up:
	./scripts/start.sh up

infra-down:
	./scripts/start.sh down

infra-status:
	./scripts/start.sh status

# ── 模擬器 ──
sim-ok:
	cd datapipe && python main.py --furnace C1 --csv data/C2_growthIdx_1.csv

sim-ng:
	cd datapipe && python main.py --furnace C2 --csv data/C2_growthIdx_5.csv

sim-both:
	cd datapipe && python main.py --furnace C1 --csv data/C2_growthIdx_1.csv &
	cd datapipe && python main.py --furnace C2 --csv data/C2_growthIdx_5.csv

# ── Build ──
build-all: build-gateway build-flink build-frontend

build-gateway:
	cd services && mvn clean package -DskipTests

build-flink:
	cd flink-jobs && mvn clean package -DskipTests
	@echo "Submit: flink run -c com.twin.flink.job.FurnaceStreamJob target/flink-jobs.jar"

build-frontend:
	cd frontend && npm install && npm run build

# ── Test ──
test:
	cd services && mvn test
	cd flink-jobs && mvn test
	cd datapipe && python -m pytest tests/

# ── Clean ──
clean:
	cd services && mvn clean
	cd flink-jobs && mvn clean
	cd frontend && rm -rf dist node_modules
