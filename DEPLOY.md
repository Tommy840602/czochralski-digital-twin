# 部署到 Hetzner（CX43 / 16GB / tommy-huang.dev）

從零到跑起來，大約 40 分鐘（多數時間在等 Docker build 和憑證簽發）。

伺服器：**CX43**（8 vCPU / 16 GB / 160 GB），使用者 **`tommy`**（非 root，需要 sudo 密碼）。

---

## 0.5 記憶體：為什麼 16GB 需要特別處理

`mem_limit` 是**上限**不是**預留**——上限總和 14.5 GB 不代表真的會用到那麼多。
但 16 GB 沒有多少犯錯空間，所以這份設定做了三件事：

**① ELK 預設關閉。** Elasticsearch + Kibana + Logstash 合計 **3.0 GB**，
是最大的一塊非必要開銷，且**沒有任何應用服務依賴它們**——logstash 只是把
docker log 收進 ES 供 Kibana 查詢。兩台爐子的規模，`docker logs` 完全夠用。

需要時再開：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml --profile elk up -d
```

**② Schema Registry 預設關閉。** 程式碼從來沒用過它（Kafka 走純 JSON，沒有 Avro；
`iot-gateway/application.yml` 那個 `kafka.schema-registry` 是遺留設定，Java 端零引用）。
省 384 MB。要開的話 `--profile extras`。

**③ 每個吃記憶體的元件都鎖定「以容器為準」的調校基準。**
這是最關鍵的一點——Postgres / MongoDB / JVM **預設都讀「主機」記憶體**去算自己的
buffer 和 heap，完全無視 `mem_limit`。結果就是它們以為有 16 GB 可用，
一路長到超出容器上限，然後被 OOM killer 用 signal 9 殺掉。
**這在本機已經發生過一次，整個 TimescaleDB 進了 recovery mode。**

所以：`TS_TUNE_MEMORY=1900MB`、`--wiredTigerCacheSizeGB 0.4`、
JVM 一律 `-XX:MaxRAMPercentage=70`。

| | mem_limit 總和 |
|---|---|
| 預設啟動 | **14.5 GB** |
| `--profile elk` | +3.5 GB |
| `--profile extras` | +0.4 GB |

**部署完務必跑 `./scripts/mem-report.sh` 量真實用量**（見第 7 節）。
數字說話——寬裕就把 ELK 開回來，吃緊就升級到 CX53（32 GB）。

---

## 0. 這份設定做了什麼（先看這段）

原本的 `docker-compose.prod.yml` 對外開了 **26 個 port**，包含**沒有密碼的 Redis**、
**沒有認證的 Elasticsearch**、MongoDB、PostgreSQL、Kafka、config-server（會吐出 Slack webhook）。
把那樣的設定放到公網 IP 上，被自動化掃描攻陷是**幾小時內的必然**，不是機率問題。

現在的架構：

```
              網際網路
                 │  只有 80 / 443
            ┌────▼─────┐
            │  Caddy   │  自動 TLS（Let's Encrypt）
            └────┬─────┘
                 │  twin-net（內部網路，不對外）
   ┌─────────────┼──────────────────────────────┐
   │             │                              │
frontend    api-gateway                  Grafana / Kibana
   │             │                       Kafka UI / Flink UI
   └─────────────┴──────────────────────────────┘
                 │
   Kafka · Timescale · Mongo · Redis · ES · MQTT
   （完全不發布 host port，只有容器之間互連）
```

* **所有介面都能從公網用**：主站 + 4 個管理介面（子網域），全部走 HTTPS。
* **管理介面加 basic auth**：Kibana / Kafka UI / Flink UI / Prometheus 本身沒有認證，
  Caddy 這層擋一道。Grafana 有自己的登入頁，不疊。
* **資料層完全不對外**。要連 DB 就開 SSH 隧道（見最後一節），一行指令。

---

## 1. DNS

在網域商把這些 A record 指到伺服器 IP：

| 名稱 | 類型 | 值 |
|---|---|---|
| `@` | A | `<伺服器 IP>` |
| `www` | A | `<伺服器 IP>` |
| `grafana` | A | `<伺服器 IP>` |
| `kibana` | A | `<伺服器 IP>` |
| `kafka` | A | `<伺服器 IP>` |
| `flink` | A | `<伺服器 IP>` |
| `prometheus` | A | `<伺服器 IP>` |

**先設 DNS 再啟動 Caddy**。Let's Encrypt 要能解析到這台機器才簽得出憑證，
而且失敗次數有速率限制（同網域每週 5 次），撞到就要等一週。

驗證（每個都要回傳你的伺服器 IP）：

```bash
for h in @ www grafana kibana kafka flink prometheus; do
  echo -n "$h → "; dig +short ${h/@/}${h:+.}tommy-huang.dev | tail -1
done
```

---

## 2. 伺服器初始設定

用既有的 `tommy` 帳號登入（**不是 root**，所以每個指令都要 `sudo`，會問密碼）：

```bash
ssh tommy@178.104.225.148
```

### 2.1 Docker

```bash
sudo apt update && sudo apt install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update && sudo apt install -y \
  docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 讓 tommy 不用 sudo 就能跑 docker
sudo usermod -aG docker tommy
```

> **改完群組要重新登入才生效**：`exit` 後重新 `ssh`，然後 `docker ps` 確認不用 sudo。

### 2.2 防火牆

```bash
sudo apt install -y ufw
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable
```

> **`ufw` 是最後一道保險**。就算未來有人不小心在 compose 加回 `ports:`，
> 防火牆仍會擋住。務必啟用。

### 2.3 Swap（16 GB 機器**強烈建議**）

Hetzner 的機器預設**沒有 swap**。這代表記憶體一旦吃緊，核心不是變慢，
而是直接**挑一個 process 殺掉**——通常就是最肥的那個，也就是 Postgres。

4 GB swap 不會讓系統變快，但會把「硬當掉」變成「暫時變慢」，給你反應時間：

```bash
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 只在真的快不夠時才用 swap（預設 60 太積極，會拖慢資料庫）
echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf

sudo sysctl -p && free -h    # Swap 那行應該顯示 4.0Gi
```

### 2.4 核心參數

```bash
# Elasticsearch 要的 mmap 上限（即使 ELK 預設關閉，之後開啟時會用到）
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf
sudo sysctl -p
```

---

## 3. 取得程式碼與設定

```bash
cd ~
git clone https://github.com/Tommy840602/czochralski-digital-twin.git
cd czochralski-digital-twin

cp .env.prod.example .env.prod
chmod 600 .env.prod
```

產生所有密碼：

```bash
for k in POSTGRES_PASSWORD MONGO_PASSWORD REDIS_PASSWORD GRAFANA_ADMIN_PASSWORD JWT_SECRET; do
  echo "$k=$(openssl rand -base64 36 | tr -dc 'A-Za-z0-9' | head -c 40)"
done
```

產生管理介面的 bcrypt hash：

```bash
docker run --rm caddy caddy hash-password --plaintext '你想用的密碼'
```

把上面的輸出、`ACME_EMAIL`、`SLACK_WEBHOOK_URL` 一併填進 `.env.prod`。

> compose 對必填項用了 `${VAR:?}`，**沒填就直接啟動失敗**——這是刻意的，
> 避免弱密碼在你沒注意的情況下上線。

---

## 4. 建置與啟動

Spring 的 Dockerfile 只做 `COPY target/*.jar`，**不在 image 內編譯**，所以要先用 Maven 打包：

```bash
sudo apt install -y openjdk-21-jdk maven
cd services && mvn -q clean package -DskipTests && cd ..
cd services/flink-jobs && mvn -q clean package -DskipTests && cd ../..
```

啟動（**不帶 profile = ELK 與 schema-registry 不啟動**，這是 16GB 的預設）：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

第一次會花 10～20 分鐘（拉 image + build）。盯著 Caddy 拿憑證：

```bash
docker logs -f twin-caddy | grep -iE "certificate|error"
```

看到 `certificate obtained successfully` 就成功了。

---

## 5. 初始化資料庫

等 TimescaleDB healthy 之後：

```bash
source .env.prod

# SPC 相關 schema 與遷移（順序不能顛倒）
docker exec -i twin-timescaledb psql -U "$POSTGRES_USER" -d furnace_db < scripts/spc-schema.sql
docker exec -i twin-timescaledb psql -U "$POSTGRES_USER" -d furnace_db < scripts/spc-migrate-operation-mode.sql
docker exec -i twin-timescaledb psql -U "$POSTGRES_USER" -d furnace_db < scripts/spc-migrate-cagg.sql
```

`spc-migrate-cagg.sql` 最後的 refresh 會跑幾分鐘（把歷史資料物化）。

等 alarm-service 起來後（約 2 分鐘），建立 SPC baseline：

```bash
docker exec twin-api-gateway sh -c 'wget -qO- --post-data="" \
  --header="X-User-Perms: SPC_VIEW" --header="X-User-Name: admin" \
  --header="X-User-Id: 2" --header="X-User-Roles: ADMIN" \
  "http://alarm-service:8092/spc/baseline/rebuild/all"'
```

---

## 6. Flink Job

Flink 的 job 不會自己復活（`disableCheckpointing`，submitter 是一次性容器）。
**jobmanager 一重啟，job 就消失**——這是既有設計，部署後要記得確認：

```bash
docker exec twin-flink-jobmanager curl -s localhost:8081/jobs
```

沒有 RUNNING 的 job 就重推：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml \
  up -d --force-recreate --no-deps flink-job-submitter
```

---

## 7. 確認

### 7.1 各介面

| 網址 | 內容 | 認證 |
|---|---|---|
| https://tommy-huang.dev | 主系統 | 應用自己的登入 |
| https://grafana.tommy-huang.dev | Grafana | Grafana 登入（`ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`） |
| https://kafka.tommy-huang.dev | Kafka UI | basic auth |
| https://flink.tommy-huang.dev | Flink UI | basic auth |
| https://prometheus.tommy-huang.dev | Prometheus | basic auth |
| ~~https://kibana.tommy-huang.dev~~ | Kibana | **預設關閉**（`--profile elk` 才有） |

> Kibana 的子網域和憑證仍然會正常簽發，只是後面沒有服務 → 會看到 **502**。
> 這是預期行為，不是壞掉。

### 7.2 資料層沒有對外（**每一個都應該是 timeout / refused**）

```bash
for p in 5432 5433 6379 27017 9092 9200 1883 8085 8888; do
  nc -zv -w2 tommy-huang.dev $p 2>&1 | tail -1
done
```

### 7.3 ⭐ 記憶體實測（16GB 機器的關鍵一步）

**讓系統穩定跑滿 10 分鐘**（讓 JVM 熱起來、Timescale 開始寫入、Flink 開始消費），
再執行：

```bash
./scripts/mem-report.sh
```

它會告訴你：主機剩多少記憶體、每個容器**實際**用了多少（對比上限）、
有沒有任何容器被 OOM killer 殺過、有沒有異常重啟。

怎麼判讀 `free -h` 的 **available** 欄：

| available | 意思 | 該做什麼 |
|---|---|---|
| **> 4 GB** | 很寬裕 | 可以把 ELK 開回來：`--profile elk up -d` |
| **2～4 GB** | 剛好 | 維持現狀，不要再加東西 |
| **< 2 GB** | 危險 | 先確認 swap 有開；還是不夠就升級 CX53 |

**報告裡只要出現任何 `OOMKilled`，就直接升級，不要省。**
記憶體不足造成的資料庫損毀，省下的那 $16/月不值得。

升級路徑：Hetzner Console → 關機 → **Rescale** → 勾 `CPU and RAM only`（保留降級彈性）
→ **CX53**（16 vCPU / 32 GB，$34.99/mo）→ 開機。資料不會不見。
> 2026-07 當下 CX53 在該機房缺貨，若仍缺貨，就得換機房重建（snapshot → 新機器 → 改 DNS）。

---

## 8. 要連資料庫時（SSH 隧道）

不需要開任何 port。在**你的筆電**執行：

```bash
# TimescaleDB → 本機 15432
ssh -N -L 15432:twin-timescaledb:5432 tommy@tommy-huang.dev
# 另開一個終端機
psql -h localhost -p 15432 -U twin -d furnace_db
```

Redis / Mongo 同理，把 `twin-timescaledb:5432` 換成 `twin-redis:6379`、`twin-mongodb:27017`。

---

## 9. 日常維運

```bash
# 狀態
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
docker stats --no-stream

# 更新（改完 Java 一定要先 mvn package，Dockerfile 只 COPY jar）
git pull
cd services && mvn -q clean package -DskipTests && cd ..
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build

# 備份 TimescaleDB
docker exec twin-timescaledb pg_dump -U "$POSTGRES_USER" furnace_db | gzip > backup-$(date +%F).sql.gz
```

---

## 已知的取捨（誠實說明）

1. **記憶體沒有太多餘裕**。上限總和 14.5 GB / 16 GB。這是能跑的，但前提是
   第 0.5 節那三項設定都有生效。**部署完一定要跑 `mem-report.sh` 驗證**，
   不要假設它一定沒事——`mem_limit` 只是上限，真正會不會爆要用量才知道。

2. **ELK 預設關閉**，所以**沒有集中式 log 查詢**。要看 log 就 `docker logs <容器>`。
   兩台爐子的規模這樣夠用，但如果你想在履歷上展示 ELK，就得升到 32 GB。

3. **Elasticsearch 的 `xpack.security` 是關的**（開啟 ELK 時）。因為它不對外，
   且 Kibana 前面有 basic auth，風險可控。但若之後有其他容器被入侵，ES 就是敞開的。

4. **Kafka / MQTT 沒有認證**。同上——只在內部網路，不對外。

5. **Flink job 不會自動恢復**。checkpoint 是關的，jobmanager 重啟後要手動重推（見第 6 節）。
   要修的話得開 checkpointing 並設定 HA。

6. **Flink 平行度降到 1**（原本 2），task slot 降到 2（原本 4）。
   `FurnaceStreamJob` 本來就 `env.setParallelism(1)`，submitter 也是傳 `parallelism:1`，
   所以**實際行為沒有改變**，只是不再為用不到的 slot 預留記憶體。

7. **`docker-compose.prod.yml` 是從 dev 版轉出來的**。改設定請先改 `docker-compose.yml`，
   再重新生成，否則兩邊會分歧（這正是舊版 prod 檔失效的原因）。
   ⚠ 但**這一輪的記憶體調校是直接改 prod 檔的**，重新生成會蓋掉——
   要重生成的話記得把 `gen-prod-compose.py` 一併更新。
