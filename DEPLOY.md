# 部署到 Hetzner（CX43 / 16GB / twin.tommy-huang.dev）

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

| | mem_limit 總和 | **實測用量** |
|---|---|---|
| 預設啟動 | 15.4 GB | **4.6 GB** |
| `--profile elk` | +3.5 GB | 約 +2～2.5 GB |
| `--profile extras` | +0.4 GB | — |

### 實測結果（2026-07-12，CX43 / 16 GB，五台爐子全速跑）

```
Mem:  15Gi total   5.7Gi used   9.6Gi available
Swap: 4.0Gi        512Ki used          ← 幾乎沒動
容器實際用量總計：4.64 GB（上限的 32%）
OOM kill：0    異常重啟：0
```

**16 GB 綽綽有餘。** 這點值得說清楚，因為它推翻了一開始的判斷：
最早我們是照「mem_limit 加總 26.8 GB」去推論 16 GB 不夠、準備升級到 32 GB 的機器。
但 **`mem_limit` 是上限不是預留**——容器不會真的吃掉配額。
實際只用 4.6 GB，連三分之一都不到。

教訓：**先量再決定。** `docker stats` 花三十秒，比多付幾倍的機器錢划算。

**部署完務必跑 `./scripts/mem-report.sh`**（見第 7 節）。判讀方式見該節的表。

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
| **`twin`** | A | `<伺服器 IP>` | ← **主站** |
| `@` | A | `<伺服器 IP>` | 301 轉到 `twin` |
| `www` | A | `<伺服器 IP>` | 301 轉到 `twin` |
| `grafana` | A | `<伺服器 IP>` |
| `kibana` | A | `<伺服器 IP>` |
| `kafka` | A | `<伺服器 IP>` |
| `flink` | A | `<伺服器 IP>` |
| `prometheus` | A | `<伺服器 IP>` |

> **Cloudflare 一律用 DNS only（灰雲）。** 開了 proxy（橘雲）Let's Encrypt 的
> HTTP-01 / TLS-ALPN 挑戰會失敗，憑證簽不出來。

### 「主站網址」和「根網域」是兩件事

| 變數 | 值 | 用途 |
|---|---|---|
| `DOMAIN` | `tommy-huang.dev` | 根網域。子網域（grafana / kafka / flink / prometheus）掛在它下面 |
| `APP_HOST` | `twin.tommy-huang.dev` | **主站**。使用者實際打開的那個 |

compose 裡的 **CORS 白名單、WebSocket allowed origins、OAuth callback、
密碼重設連結** 全部從 `APP_HOST` 推導——換網址只要改 `.env.prod` 一行。

⚠ **但三家 OAuth App 後台的 callback URL 要另外手動改**，那在外部系統：

```
https://<APP_HOST>/api/login/oauth2/code/github
https://<APP_HOST>/api/login/oauth2/code/google
https://<APP_HOST>/api/login/oauth2/code/azure
```

忘了改的症狀是 `redirect_uri_mismatch`（錯誤頁會印出它收到的 URI，好認）。

**先設 DNS 再啟動 Caddy**。Let's Encrypt 要能解析到這台機器才簽得出憑證，
而且失敗次數有速率限制（同網域每週 5 次），撞到就要等一週。

驗證（每個都要回傳你的伺服器 IP）：

```bash
for h in @ www twin grafana kibana kafka flink prometheus; do
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
| **https://twin.tommy-huang.dev** | **主系統** | 應用自己的登入 |
| https://tommy-huang.dev | → 301 轉到 `twin.` | — |
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

## 全新機器部署時「一定會踩」的坑（實戰紀錄）

2026-07-12 首次部署到全新伺服器，一共踩到 **16 個問題**。全部都修了，
但如果你之後換機器、或看到類似症狀，這張表能省下好幾小時。

先講**共通模式**，因為它比任何單一 bug 都重要：

> **「本機能跑」的狀態，有很大一部分是靠手動操作和 localhost 的巧合撐著的，
> 從來沒寫進程式碼或設定檔。**

具體有三種型態，每一種都在這次咬了我們好幾次：

1. **靠手動 SQL 撐著的** —— `authdb`、`furnace_registry`、`oee_target`
   都是某次在筆電上手動建的，從沒進過任何 `.sql`。新機器上一片空白。
2. **靠 localhost 撐著的** —— CORS 白名單、WebSocket origins、OAuth redirect、
   前端 API base URL。dev 剛好就是 localhost，所以永遠不會發現寫死了。
3. **從來沒被執行過的程式碼** —— OAuth callback、註冊 action、簡訊驗證。
   dev 沒設定 OAuth，那條路徑根本進不去；註冊在 UI 上從來沒成功過。
   **部署不是「造成」這些 bug，而是第一次讓這些程式碼真的跑起來。**

---

### 起不來 / 無限重啟

| 症狀 | 真正的原因 | 為什麼 dev 測不出來 |
|---|---|---|
| Caddy 無限重啟 | `auto_https on` 不是合法值（只接受 `off` / `disable_*`） | dev 沒有 Caddy |
| auth-service 無限重啟 | **`authdb` 這個資料庫從沒被建立**（compose 只建 `furnace_db`） | 筆電上手動 `CREATE DATABASE` 過 |
| auth-service 拒絕啟動 | JWT secret **base64 解碼後**只有 30 bytes，HS512 要 64。要用 `openssl rand -base64 64` | dev 的 secret 剛好夠長 |
| auth-service 拒絕啟動 | OAuth `client-id` 是空字串 → `Client id must not be empty`。**任何一組空的都會擋** | dev 的 `.env` 有填值 |
| Flink job 無限重啟 | Redis 密碼**只給了 TaskManager**（見下方說明） | dev 的 Redis 沒密碼 |

### 起來了，但瀏覽器用不了（curl 測都正常 → 最難查的一類）

| 症狀 | 真正的原因 |
|---|---|
| 登入失敗、但 curl 打 API 回 200 | `VITE_API_URL=http://YOUR_SERVER_IP/api` —— **範本佔位字串從沒被替換**。dev 讀 `.env.development` 所以永遠正常 |
| 所有 POST 回 403 | Gateway 的 CORS 白名單只有 localhost。**依 Fetch 規範，非 GET/HEAD 的請求即使同源也會帶 `Origin`**，所以 GET 全過、POST 全掛；curl 不送 Origin，怎麼測都 200 |
| WebSocket 403、前端永遠 OFFLINE | `WebSocketConfig` 的 allowed origins 也寫死 localhost。**`/ws` 由 nginx 直接代理到 furnace-service，不經過 gateway**，所以 gateway 的白名單管不到，得各自設定 |
| 登入 API 一律 404 | 前端 nginx 沒有把 `/api/auth`、`/api/oauth2`、`/api/login` 的 `/api` 前綴剝掉。gateway 的路由是 `/auth/**`（無前綴），dev 靠 vite proxy rewrite，正式站沒人做 |

### 起來了，但「安靜地」不動（沒有任何錯誤訊息）

| 症狀 | 真正的原因 |
|---|---|
| **前端 0 爐、3D 空白、Flink job 顯示 RUNNING、零錯誤** | **`furnace_registry` 是空表** → `RegistryFilter` 的 `knownFurnaces.contains()` 永遠 false → 每一筆 Kafka 訊息都被丟掉。Job 確實在跑，只是什麼都沒做 |
| OEE 頁面 500 | **`oee_target` 只存在於 JPA entity，從沒有任何 SQL 建過它**。alarm-service 是 `ddl-auto: none` 不會自動建表 |
| SPC 沒有管制界線、八條規則全 0 | 這**不是 bug**。`MIN_SUBGROUPS = 30`——不滿 30 個一分鐘子群、或 CV > 10%（非穩態）就不建 baseline。用 10 分鐘的資料算出來的 σ 是騙人的 |

### OAuth / 註冊 / 郵件（這些路徑在 dev 從來沒被執行過）

| 症狀 | 真正的原因 |
|---|---|
| OAuth 授權成功、卻被導回 `localhost:5173` | 程式讀的是 `app.oauth.frontend-callback`，compose 注入的是 `OAUTH_FRONTEND_CALLBACK`——**兩者對不上**，yaml 裡沒有任何一段接這個變數。密碼重設信裡的連結也是 localhost（同一個 bug） |
| OAuth 登入後每個 API 都 401 | `OAuthCallbackView` 用**物件**呼叫 `setSession(...)`，但它收的是**位置參數** → `Authorization: Bearer [object Object]`。帳密登入沒事，只有 OAuth 這條路徑會踩到 |
| UI 註冊永遠「註冊失敗」 | **auth store 裡根本沒有 `register` action**，`RegisterView` 卻呼叫 `auth.register({...})` → `undefined is not a function` → 被 catch → 顯示成「註冊失敗」。後端其實是好的，curl 打得通 |
| 註冊「發送驗證碼」失敗 | auth-service 沒拿到 `REDIS_PASSWORD` → `NOAUTH` → 驗證碼存不進 Redis |
| 忘記密碼沒收到信 | `EMAIL_PROVIDER` 預設 `log`，只把連結印在 log。要真的寄信得設 `EMAIL_PROVIDER=smtp` + Gmail 應用程式密碼（`MAIL_FROM` **必須是自己的 Gmail**，Gmail SMTP 不讓你用別的網域當寄件人） |

### 安全性（這三個最值得記住）

| 問題 | 說明 |
|---|---|
| **明文密碼寫進 log** | `logging.level.org.springframework.web: DEBUG` 會把**每個 request body 原封不動印出來**，包含 `/auth/login` 的明文密碼。這些 log 會進 `docker logs`，開了 ELK 還會進 Elasticsearch |
| **註冊完全不驗簡訊碼** | `SmsCodeService.verify()` 早就寫好了，但**沒有任何人呼叫它**；`REGISTER_SMS_REQUIRED=true` 也沒有任何地方讀取。任何人 POST `/auth/register` 帶一個亂填的驗證碼（或完全不帶）都能註冊成功。UI 上有 reCAPTCHA、有簡訊、有倒數計時，看起來一應俱全——**後端一行都沒驗**。這種「看起來有做」的洞比明顯的洞更危險 |
| **報告生成漏權限判斷** | 導覽列的「報告生成」沒有 `v-if="hasPermission('REPORT_GEN')"`、路由也沒有 `meta.requiresPerm`，跟旁邊的 SPC / OEE 不一致 |

---

### 兩個特別值得記住的機制

**① Flink 的 `main()` 執行在 JobManager，不是 TaskManager。**

```java
static final String RPASS = System.getenv()...          // 類別載入時求值
stream.sinkTo(new RedisSink(RHOST, RPORT, RPASS));      // 在 main() 裡建構
```

`main()` 是在 JobManager 建 execution graph 時跑的。密碼在那裡讀出來、**序列化進 sink 物件**，才送去 TaskManager 執行。只有寫在 `open()` 裡的才是真的在 TaskManager 讀 env。

`TimescaleDbSink` 用 `open()`、`RedisSink` 用建構子 —— 所以**資料進得了 Postgres 卻進不了 Redis**，症狀極度誤導。

**② 同源也會觸發 CORS。**

依 Fetch 規範，**非 GET/HEAD 的請求（POST/PUT/DELETE）即使同源，瀏覽器也會帶 `Origin` header**。Gateway 一看到 `Origin` 就套 CORS 規則。所以「前端和 API 同源，不該有 CORS 問題」這個直覺是**錯的**——GET 全過、POST 全 403，而 curl 不送 Origin，怎麼測都是 200。

---

## 磁碟：會慢慢塞滿，要主動清

每次 CI 部署都 `--build`，Docker 的 image 與 build cache 持續累積。
**上線約 3 週後 160GB 磁碟塞滿**，Kafka 和 MongoDB（一直在寫檔的）
首當其衝被拖垮，`furnace_metrics` 停止寫入、前端面板變空——而且症狀誤導：
Flink job 顯示 RUNNING、Postgres 也 healthy，看不出是磁碟問題，
要 `df -h /` 才會看到 `No space left on device`。

三道防線（都已進 repo）：

1. **CI 部署時 `docker system prune -af` + `builder prune -af`**——
   每次部署順手清掉 build cache（`image prune -f` 不夠，它不碰 build cache）。
2. **`scripts/disk-guard.sh` 每日排程**——清理 + 超過 80% 發 Slack 告警。
   安裝（伺服器上跑一次）：
   ```bash
   ( crontab -l 2>/dev/null; \
     echo "17 4 * * * cd $HOME/czochralski-digital-twin && ./scripts/disk-guard.sh >> /tmp/disk-guard.log 2>&1" \
   ) | crontab -
   ```
3. **CI 冒煙測試會在清理後印出使用率**，超過 85% 發 `::warning::`。

手動救援（磁碟已滿時）：
```bash
docker system prune -af && docker builder prune -af
df -h /
# 然後照第 6 節重推 Flink job；Kafka/Mongo 若被拖垮，docker compose up -d 拉回來
```

---

## 已知的取捨（誠實說明）

1. **記憶體很寬裕**。實測 4.6 GB / 16 GB，available 9.6 GB。
   當初擔心 16 GB 不夠是誤判——那是拿 `mem_limit` 加總去推論，
   但 `mem_limit` 是上限不是預留。**先量再決定。**

2. **ELK 預設關閉**，所以**沒有集中式 log 查詢**。要看 log 就 `docker logs <容器>`。
   記憶體其實開得起（+2～2.5 GB，還剩 7 GB），是刻意選擇不開，保持系統單純。
   要開：`--profile elk up -d`，並把 `Caddyfile` 裡 kibana 那段取消註解。

3. **Elasticsearch 的 `xpack.security` 是關的**（開啟 ELK 時）。因為它不對外，
   且 Kibana 前面有 basic auth，風險可控。但若之後有其他容器被入侵，ES 就是敞開的。

4. **Kafka / MQTT 沒有認證**。同上——只在內部網路，不對外。

5. **Flink job 不會自動恢復**。checkpoint 是關的（job code 裡 `disableCheckpointing()`
   蓋掉了 FLINK_PROPERTIES 的設定），jobmanager 重啟後要手動重推（見第 6 節）。
   要徹底修得開 checkpointing 並設定 HA。

   **⚠ JobManager Metaspace OOM（實戰：上線 12 天後面板變空）**
   每次 job 提交都會在 JobManager 的 Metaspace 留一個 classloader 不釋放
   （Flink 已知行為）。多次部署 + 重推累積後 Metaspace 爆掉，JobManager
   卡在「進程活著、`/jobs` 回 500」的殭屍狀態，資料流靜靜地斷掉。
   已做的緩解：
   - `jvm-metaspace.size` 256m → **512m**（`mem_limit` 也跟著 1g → 1536m），大幅拉長爆掉的時間
   - healthcheck 從 `/overview` 改打 **`/jobs`**——OOM 時 `/overview` 還會回 200
     但 `/jobs` 會 500，改打 `/jobs` 才能讓 `restart:always` 自動重建 JobManager
   - job 的 restart strategy 從「21 億次固定重試」改成**有上限的指數退避**，
     持續性錯誤會放棄並進 FAILED，被 healthcheck / CI 冒煙測試抓到，而不是無聲空轉
   - 復原 SOP：`up -d --force-recreate flink-jobmanager flink-taskmanager`
     → 等 `/jobs` 回 `{"jobs":[]}` → `up -d --force-recreate --no-deps flink-job-submitter`
     → 確認**剛好一個** RUNNING（重啟過程有時會多推一個，多的要 cancel）

6. **Flink 平行度降到 1**（原本 2），task slot 降到 2（原本 4）。
   `FurnaceStreamJob` 本來就 `env.setParallelism(1)`，submitter 也是傳 `parallelism:1`，
   所以**實際行為沒有改變**，只是不再為用不到的 slot 預留記憶體。

7. **`docker-compose.prod.yml` 是從 dev 版轉出來的**。改設定請先改 `docker-compose.yml`，
   再重新生成，否則兩邊會分歧（這正是舊版 prod 檔失效的原因）。
   ⚠ 但**這一輪的記憶體調校是直接改 prod 檔的**，重新生成會蓋掉——
   要重生成的話記得把 `gen-prod-compose.py` 一併更新。

8. **註冊實質上是關閉的**。`SMS_PROVIDER=log`——驗證碼只印在
   `docker logs auth-service`，外人拿不到，所以註冊不了。
   而後端**現在會真的驗**驗證碼（先前完全不驗，見上方安全性那節），
   所以這是一道有效的門。這是刻意的選擇：訪客用 GitHub / Google / Outlook
   登入即可（一律 VIEWER），不需要註冊，也少一個對外的攻擊面。

   要真的開放註冊：接 Twilio（付費帳號才能發給任意號碼，台灣約 $0.05–0.09/則），
   或把 `REGISTER_SMS_REQUIRED` 設成 `false`——但 reCAPTCHA 的 secret 也沒設
   （目前是「secret 未設定就略過驗證」），等於完全沒有防機器人。

9. **OAuth 使用者一律 VIEWER**（`OAuth2ProvisioningService.DEFAULT_ROLE`）。
   只看得到 3D 孿生和儀表板。SPC / OEE / 報告要 ENGINEER 以上。
   **不要為了展示方便把預設角色改成 ADMIN**——這是公開站台，
   那等於「任何人有 GitHub 帳號就是管理員」。要展示就用本地 `admin` 帳號登入。

10. **AI 報告會消耗 OpenAI 額度**，帳單是自己的。這也是把 `REPORT_GEN`
    留在 ENGINEER 以上、不給 VIEWER 的另一個理由。
