import paho.mqtt.client as mqtt
from src.utils.logger import get_logger

log = get_logger('mqtt.publisher')

class MqttPublisher:
    def __init__(self, host, port, keepalive=60):
        self.host = host; self.port = port; self.keepalive = keepalive
        self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect
        self.connected = False

    def _on_connect(self, client, userdata, flags, reason_code, properties):
        self.connected = (reason_code == 0)
        if self.connected: log.info(f'已連線 {self.host}:{self.port}')
        else: log.error(f'連線失敗 rc={reason_code}')

    def _on_disconnect(self, client, userdata, flags, reason_code, properties):
        self.connected = False
        log.warning('MQTT 已斷線')

    def connect(self):
        self.client.connect(self.host, self.port, self.keepalive)
        self.client.loop_start()

    def publish(self, topic, payload, qos=1):
        if not self.connected:
            log.warning(f'未連線，跳過 {topic}'); return False
        result = self.client.publish(topic, payload, qos=qos)
        if result.rc == mqtt.MQTT_ERR_SUCCESS:
            log.debug(f'→ {topic}'); return True
        log.error(f'發送失敗 rc={result.rc}'); return False

    def disconnect(self):
        self.client.loop_stop(); self.client.disconnect()
