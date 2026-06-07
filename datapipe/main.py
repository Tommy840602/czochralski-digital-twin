"""
長晶爐數位孿生 — Python Datapipe
用法：
  python main.py --furnace C1 --csv data/C2_growthIdx_1.csv
  python main.py --furnace C2 --csv data/C2_growthIdx_5.csv
"""
import argparse
import sys
import time
import threading
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

# 全域 emitter 供 API server 控制
_emitter: EventEmitter = None

class SpeedHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        parsed = urlparse(self.path)
        if parsed.path == '/simulator/speed':
            params = parse_qs(parsed.query)
            try:
                speed = int(params.get('value', ['1'])[0])
                if _emitter:
                    _emitter.set_speed(speed)
                    log.info(f'速度已設為 {speed}x')
                self.send_response(200)
                self.end_headers()
                self.wfile.write(f'{{"speed":{speed}}}'.encode())
            except Exception as e:
                self.send_response(400)
                self.end_headers()
                self.wfile.write(str(e).encode())
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        pass  # 靜音 HTTP log


def start_api_server(port=8099):
    server = HTTPServer(('0.0.0.0', port), SpeedHandler)
    log.info(f'Speed API 監聽 http://localhost:{port}/simulator/speed')
    server.serve_forever()


def load_config(path='config/settings.yaml'):
    p = Path(path)
    if not p.exists():
        log.error(f'找不到設定檔：{path}')
        sys.exit(1)
    with open(p) as f:
        return yaml.safe_load(f)


def main():
    global _emitter

    parser = argparse.ArgumentParser(description='長晶爐 CSV → MQTT 模擬器')
    parser.add_argument('--furnace',  required=True)
    parser.add_argument('--csv',      required=True)
    parser.add_argument('--interval', type=float)
    parser.add_argument('--host')
    parser.add_argument('--port',     type=int)
    parser.add_argument('--api-port', type=int, default=8099)
    args = parser.parse_args()

    cfg      = load_config()
    mqtt_cfg = cfg.get('mqtt', {})
    sim_cfg  = cfg.get('simulator', {})

    host     = args.host     or mqtt_cfg.get('host', 'localhost')
    port     = args.port     or mqtt_cfg.get('port', 1883)
    interval = args.interval or sim_cfg.get('interval_seconds', 10)
    qos      = mqtt_cfg.get('qos', 1)

    log.info(f'爐={args.furnace} CSV={args.csv} MQTT={host}:{port} 間隔={interval}s')

    rows = read_csv(args.csv)
    if not rows:
        log.error('CSV 無資料')
        sys.exit(1)

    pub = MqttPublisher(host, port)
    pub.connect()
    for _ in range(10):
        if pub.connected:
            break
        time.sleep(0.5)
    else:
        log.error('無法連線 MQTT')
        sys.exit(1)

    _emitter = EventEmitter(pub, args.furnace, interval=interval, qos=qos)

    # 啟動 Speed API server（背景執行緒）
    api_thread = threading.Thread(
        target=start_api_server,
        args=(args.api_port,),
        daemon=True
    )
    api_thread.start()

    try:
        _emitter.emit_all(rows)
    except KeyboardInterrupt:
        log.info('使用者中止')
    finally:
        pub.disconnect()


if __name__ == '__main__':
    main()
