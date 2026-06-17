"""
長晶爐數位孿生 — Python Datapipe（彈性多爐版）
用法：
  # 模擬單台爐子
  python main.py --furnace D1 --csv data/D1_all.csv

  # 模擬所有設定檔中的爐子（批次啟動）
  python main.py --all

  # 指定自訂間隔
  python main.py --furnace F7 --csv data/F7_all.csv --interval 5

  # 速度調整 API（執行中）
  curl -X POST "http://localhost:8099/simulator/speed?value=10"
"""

import argparse
import sys
import time
import threading
import subprocess
import yaml
from pathlib import Path
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

sys.path.insert(0, str(Path(__file__).parent))

from src.utils.logger        import get_logger
from src.mqtt.publisher      import MqttPublisher
from src.simulator.csv_reader    import read_csv
from src.simulator.event_emitter import EventEmitter

log = get_logger('main')

# 全域 emitters（支援多台）
_emitters: dict = {}
_lock = threading.Lock()


class SpeedHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        parsed = urlparse(self.path)
        if parsed.path == '/simulator/speed':
            params = parse_qs(parsed.query)
            furnace = params.get('furnace', [None])[0]
            try:
                speed = int(params.get('value', ['1'])[0])
                with _lock:
                    targets = ([_emitters[furnace]] if furnace and furnace in _emitters
                               else list(_emitters.values()))
                for emitter in targets:
                    emitter.set_speed(speed)
                self.send_response(200)
                self.end_headers()
                msg = f'{{"speed":{speed},"furnaces":{[e.furnace_id for e in targets]}}}'
                self.wfile.write(msg.encode())
            except Exception as e:
                self.send_response(400)
                self.end_headers()
                self.wfile.write(str(e).encode())
        elif parsed.path == '/simulator/status':
            self.send_response(200)
            self.end_headers()
            with _lock:
                status = {
                    fid: {'speed': e.speed, 'interval': e._current_interval()}
                    for fid, e in _emitters.items()
                }
            import json
            self.wfile.write(json.dumps(status).encode())
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        pass


def start_api_server(port: int = 8099):
    server = HTTPServer(('0.0.0.0', port), SpeedHandler)
    log.info(f'Speed API 監聽 :{port}  '
             f'POST /simulator/speed?value=N[&furnace=D1]')
    server.serve_forever()


def load_config(path: str = 'config/settings.yaml') -> dict:
    p = Path(path)
    if not p.exists():
        log.error(f'找不到設定檔：{path}')
        sys.exit(1)
    with open(p) as f:
        return yaml.safe_load(f)


def run_single(furnace_id: str, csv_path: str, interval: float,
               host: str, port: int, qos: int, api_port: int):
    """執行單台爐子模擬"""
    rows = read_csv(csv_path)
    if not rows:
        log.error(f'CSV 無資料：{csv_path}')
        return

    pub = MqttPublisher(host, port)
    pub.connect()
    for _ in range(10):
        if pub.connected:
            break
        time.sleep(0.5)
    else:
        log.error('無法連線 MQTT')
        return

    emitter = EventEmitter(pub, furnace_id, interval=interval, qos=qos)
    with _lock:
        _emitters[furnace_id] = emitter

    try:
        emitter.emit_all(rows)
    except KeyboardInterrupt:
        log.info(f'爐 {furnace_id} 中止')
    finally:
        pub.disconnect()
        with _lock:
            _emitters.pop(furnace_id, None)


def run_all(cfg: dict, host: str, port: int, interval: float):
    """批次啟動設定檔中所有爐子（每台一個執行緒）"""
    furnaces = cfg.get('furnaces', [])
    if not furnaces:
        log.error('settings.yaml 中 furnaces 清單為空')
        sys.exit(1)

    qos = cfg.get('mqtt', {}).get('qos', 1)
    threads = []

    for f in furnaces:
        fid = f['id']
        csv  = f.get('csv', f'data/{fid}_all.csv')
        t = threading.Thread(
            target=run_single,
            args=(fid, csv, interval, host, port, qos, None),
            name=f'furnace-{fid}',
            daemon=True
        )
        threads.append(t)
        log.info(f'準備啟動 爐={fid}  csv={csv}')

    for t in threads:
        t.start()
        time.sleep(0.3)  # 稍微錯開，避免同時搶 MQTT 連線

    log.info(f'所有爐子已啟動：{[f["id"] for f in furnaces]}')

    try:
        for t in threads:
            t.join()
    except KeyboardInterrupt:
        log.info('使用者中止所有模擬')


def main():
    parser = argparse.ArgumentParser(description='長晶爐 CSV → MQTT 模擬器（彈性多爐版）')
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--furnace', help='爐子 ID（等同 CSV PULLER 值，如 D1 / FA）')
    mode.add_argument('--all',     action='store_true', help='批次啟動 settings.yaml 中所有爐子')

    parser.add_argument('--csv',      help='CSV 檔案路徑（--furnace 模式必填）')
    parser.add_argument('--interval', type=float, help='每筆資料間隔秒數')
    parser.add_argument('--host',     help='MQTT broker host')
    parser.add_argument('--port',     type=int,   help='MQTT broker port')
    parser.add_argument('--api-port', type=int,   default=8099)
    args = parser.parse_args()

    cfg      = load_config()
    mqtt_cfg = cfg.get('mqtt', {})
    sim_cfg  = cfg.get('simulator', {})

    host     = args.host     or mqtt_cfg.get('host',             'localhost')
    port     = args.port     or mqtt_cfg.get('port',             1883)
    qos      = mqtt_cfg.get('qos', 1)
    interval = args.interval or sim_cfg.get('interval_seconds',  10)

    # 啟動 Speed API（背景執行緒）
    api_thread = threading.Thread(
        target=start_api_server,
        args=(args.api_port,),
        daemon=True
    )
    api_thread.start()

    if args.all:
        run_all(cfg, host, port, interval)
    else:
        if not args.furnace:
            parser.error('請指定 --furnace 或 --all')
        if not args.csv:
            parser.error('--furnace 模式需指定 --csv')
        run_single(args.furnace, args.csv, interval, host, port, qos, args.api_port)


if __name__ == '__main__':
    main()
