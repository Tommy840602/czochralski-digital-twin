# ============================================================
#  長晶爐數位孿生 — Makefile（彈性多爐版）
# ============================================================
.PHONY: help infra-up infra-down infra-status \
        import-csv import-csv-dry \
        sim sim-all sim-D1 sim-D3 sim-DB sim-F7 sim-FA \
        build-all build-flink build-frontend \
        test clean

help:
	@echo ""
	@echo "  長晶爐數位孿生 — 指令清單（彈性多爐版）"
	@echo "  ─────────────────────────────────────────────"
	@echo "  make infra-up           啟動所有基礎設施"
	@echo "  make infra-down         停止基礎設施"
	@echo "  make infra-status       查看服務狀態"
	@echo ""
	@echo "  make import-csv         匯入 datapipe/data/ 所有 *_all.csv → TimescaleDB"
	@echo "  make import-csv-dry     乾跑（只驗證，不寫入）"
	@echo "  make import-file f=路徑  匯入單一 CSV"
	@echo ""
	@echo "  make sim f=D1           模擬指定爐子（讀 settings.yaml 取 csv 路徑）"
	@echo "  make sim-all            批次模擬所有爐子"
	@echo "  make sim-D1             模擬爐子 D1"
	@echo "  make sim-D3             模擬爐子 D3"
	@echo "  make sim-DB             模擬爐子 DB"
	@echo "  make sim-F7             模擬爐子 F7"
	@echo "  make sim-FA             模擬爐子 FA"
	@echo ""
	@echo "  make speed v=10         調整所有爐子速度（預設 1x）"
	@echo "  make speed f=D1 v=10    調整指定爐子速度"
	@echo ""
	@echo "  make build-all          Build 所有服務"
	@echo "  make build-flink        Build Flink Job JAR"
	@echo "  make build-frontend     Build Vue 3 前端"
	@echo "  make test               執行所有測試"
	@echo "  make clean              清理 build artifacts"
	@echo ""

# ── 基礎設施 ──────────────────────────────────────────────
infra-up:
	./scripts/start.sh up

infra-down:
	./scripts/start.sh down

infra-status:
	./scripts/start.sh status

# ── CSV 匯入 ──────────────────────────────────────────────
DB_URL ?= postgresql://twin:twin_secret@localhost:5433/furnace_db

import-csv:
	cd datapipe && python import_csv.py \
		--dir data/ \
		--url $(DB_URL) \
		--batch-size 5000

import-csv-dry:
	cd datapipe && python import_csv.py \
		--dir data/ \
		--url $(DB_URL) \
		--dry-run

import-file:
	@if [ -z "$(f)" ]; then echo "用法: make import-file f=path/to/file.csv"; exit 1; fi
	cd datapipe && python import_csv.py \
		--file $(f) \
		--url $(DB_URL)

# ── 模擬器 ──────────────────────────────────────────────
sim-all:
	cd datapipe && python main.py --all

sim:
	@if [ -z "$(f)" ]; then echo "用法: make sim f=D1"; exit 1; fi
	cd datapipe && python main.py \
		--furnace $(f) \
		--csv data/$(f)_all.csv

sim-D1:
	cd datapipe && python main.py --furnace D1 --csv data/D1_all.csv
sim-D3:
	cd datapipe && python main.py --furnace D3 --csv data/D3_all.csv
sim-DB:
	cd datapipe && python main.py --furnace DB --csv data/DB_all.csv
sim-F7:
	cd datapipe && python main.py --furnace F7 --csv data/F7_all.csv
sim-FA:
	cd datapipe && python main.py --furnace FA --csv data/FA_all.csv

# ── 速度調整 API ──────────────────────────────────────────
v   ?= 1
f   ?=
speed:
	@if [ -n "$(f)" ]; then \
		curl -s -X POST "http://localhost:8099/simulator/speed?value=$(v)&furnace=$(f)"; \
	else \
		curl -s -X POST "http://localhost:8099/simulator/speed?value=$(v)"; \
	fi
	@echo ""

# ── Build ─────────────────────────────────────────────────
build-all: build-flink build-frontend

build-gateway:
	cd services && mvn clean package -DskipTests

build-flink:
	cd services/flink-jobs && mvn clean package -DskipTests
	@echo "Submit: flink run -c com.twin.flink.job.FurnaceStreamJob target/flink-jobs.jar"

build-frontend:
	cd frontend && npm install && npm run build

# ── Test ─────────────────────────────────────────────────
test:
	cd services && mvn test
	cd services/flink-jobs && mvn test
	cd datapipe && python -m pytest tests/ -v

# ── Clean ─────────────────────────────────────────────────
clean:
	cd services && mvn clean
	cd services/flink-jobs && mvn clean
	cd frontend && rm -rf dist node_modules
