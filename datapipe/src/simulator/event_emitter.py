"""
長晶爐數位孿生 — EventEmitter（彈性多爐版）
furnace_id 直接從 CSV PULLER 欄位讀取，不需外部指定。
支援動態速度調整 API（POST /simulator/speed?value=N）
"""

import json
import time
import threading
from datetime import datetime
from src.utils.logger import get_logger
from src.utils.schema import row_to_payload, get_furnace_id, to_json

log = get_logger('simulator.event_emitter')


class EventEmitter:
    def __init__(self, publisher, furnace_id: str, interval: float = 10.0, qos: int = 1):
        """
        Args:
            publisher:   MqttPublisher 實例
            furnace_id:  爐子 ID（來自 --furnace 參數，等同 CSV PULLER 值）
            interval:    基礎間隔秒數
            qos:         MQTT QoS
        """
        self.publisher   = publisher
        self.furnace_id  = furnace_id
        self.interval    = interval
        self.qos         = qos
        self.speed       = 1
        self.topic_data  = f'furnace/{furnace_id}'   # 動態 topic：furnace/D1, furnace/FA ...
        self.topic_alarm = 'furnace/alarm'
        self._lock       = threading.Lock()

    def set_speed(self, speed: int):
        with self._lock:
            self.speed = max(1, speed)
            log.info(f'爐 {self.furnace_id} 速度 → {self.speed}x  '
                     f'間隔 = {self.interval / self.speed:.1f}s')

    def _current_interval(self) -> float:
        with self._lock:
            return self.interval / self.speed

    def emit_all(self, rows: list):
        log.info(f'開始模擬 爐={self.furnace_id}  共 {len(rows):,} 筆')
        for i, row in enumerate(rows):
            # 從 CSV 取得真實 furnace_id（可與 self.furnace_id 相同）
            csv_furnace_id = get_furnace_id(row)
            if csv_furnace_id != self.furnace_id:
                log.warning(f'CSV PULLER={csv_furnace_id} 與 --furnace={self.furnace_id} 不符，'
                            f'以 CSV 值為準')

            payload  = row_to_payload(row)
            json_str = to_json(payload)

            self.publisher.publish(self.topic_data, json_str, self.qos)

            if (i + 1) % 100 == 0 or i == 0:
                log.info(f'[{i+1}/{len(rows)}] furnace={payload.get("furnaceId")} '
                         f'mode={payload.get("operationMode")} '
                         f'Ø={payload.get("diameter")} '
                         f'T={payload.get("heaterTemp")}°C '
                         f'({self.speed}x)')

            # 最後一筆且是 NG 爐（或爐子已完成）→ 發告警
            if i == len(rows) - 1:
                self._send_completion_alarm(payload)

            if i < len(rows) - 1:
                time.sleep(self._current_interval())

        log.info(f'爐 {self.furnace_id} 模擬完畢')

    def _send_completion_alarm(self, p: dict):
        """CSV 播完時發送告警通知（不區分 OK/NG，統一通知）"""
        alarm = {
            'alarmType':   'CsvEnd',
            'furnaceId':   self.furnace_id,
            'ingotNo':     p.get('ingotNo'),
            'severity':    'INFO',
            'message':     f'[長晶爐 {self.furnace_id}] CSV 播放完畢',
            'triggeredAt': datetime.utcnow().isoformat() + 'Z',
            'isResolved':  True,
            'slackSent':   False,
            'context': {
                'diameter':       p.get('diameter'),
                'heaterTemp':     p.get('heaterTemp'),
                'operationMode':  p.get('operationMode'),
                'grMean':         p.get('grMean'),
            }
        }
        self.publisher.publish(self.topic_alarm, json.dumps(alarm), self.qos)
        log.info(f'📡 告警已發送：{self.furnace_id} CSV 播完')
