import time
import json
import threading
from datetime import datetime
from src.utils.logger import get_logger
from src.utils.schema import row_to_payload, to_json

log = get_logger('simulator.event_emitter')

class EventEmitter:
    def __init__(self, publisher, furnace_id, interval=10.0, qos=1):
        self.publisher   = publisher
        self.furnace_id  = furnace_id
        self.interval    = interval   # 基礎間隔（秒）
        self.qos         = qos
        self.speed       = 1          # 速度倍數
        self.topic_data  = f'furnace/{furnace_id}'
        self.topic_alarm = 'furnace/alarm'
        self._lock       = threading.Lock()

    def set_speed(self, speed):
        with self._lock:
            self.speed = max(1, speed)
            log.info(f'爐{self.furnace_id} 速度設為 {self.speed}x，間隔={self.interval/self.speed:.1f}s')

    def _current_interval(self):
        with self._lock:
            return self.interval / self.speed

    def emit_all(self, rows):
        log.info(f'開始模擬 爐{self.furnace_id} 共{len(rows)}筆')
        for i, row in enumerate(rows):
            payload  = row_to_payload(row, self.furnace_id)
            event    = payload.get('event', 1)
            json_str = to_json(payload)

            self.publisher.publish(self.topic_data, json_str, self.qos)
            log.info(f'[{i+1}/{len(rows)}] E={event} Ø={payload.get("diameter")} '
                     f'T={payload.get("heaterTemp")}°C Mode={payload.get("operationMode")} '
                     f'({self.speed}x)')

            if i == len(rows) - 1 and event == 6:
                self._send_ng_alarm(payload)

            if i < len(rows) - 1:
                time.sleep(self._current_interval())

        log.info(f'爐{self.furnace_id} 模擬完畢')

    def _send_ng_alarm(self, p):
        alarm = {
            'alarmType': 'CsvEnd', 'furnaceId': self.furnace_id,
            'ingotNo': p.get('ingotNo'), 'severity': 'CRITICAL',
            'message': f'[長晶爐{self.furnace_id}] NG CSV播完，連線中斷',
            'triggeredAt': datetime.utcnow().isoformat()+'Z',
            'isResolved': False, 'slackSent': False,
            'context': {
                'diameter': p.get('diameter'), 'heaterTemp': p.get('heaterTemp'),
                'event': p.get('event')
            }
        }
        self.publisher.publish(self.topic_alarm, json.dumps(alarm), self.qos)
        log.warning(f'⚡ NG Alarm 已發送')
