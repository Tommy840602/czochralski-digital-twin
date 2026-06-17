"""
長晶爐數位孿生 — Datapipe Schema（彈性多爐版）
CSV 欄位 → MQTT JSON payload 對應
PULLER 欄位 → furnace_id（爐子識別碼）
"""

import json
from datetime import datetime

# CSV 欄位 → JSON 欄位對應
FIELD_MAP = {
    'LogTime':                    'logTime',
    'INGOT_NO':                   'ingotNo',
    'PULLER':                     'furnaceId',           # PULLER 即 furnace_id
    'Operation Mode':             'operationMode',
    'SOP':                        'sop',
    'Diameter':                   'diameter',
    'D_mean':                     'dMean',
    'Diameter target':            'diameterTarget',
    'Heater temp':                'heaterTemp',
    'Heater temp target':         'heaterTempTarget',
    'Heater Power SV':            'heaterPowerSv',
    'HTmean':                     'htMean',
    'GR_mean':                    'grMean',
    'temp2':                      'temp2',
    'temp4':                      'temp4',
    'temp5':                      'temp5',
    'temp9':                      'temp9',
    'temp29':                     'temp29',
    'Body length':                'bodyLength',
    'Neck Length Accum':          'neckLengthAccum',
    'Seed lift':                  'seedLift',
    'Seed Lift SP':               'seedLiftSp',
    'Seed lift target':           'seedLiftTarget',
    'Crucible rotation SP':       'crucibleRotationSp',
    'CRmean':                     'crMean',
    'Crucible Lift':              'crucibleLift',
    'Crucible lift ratio':        'crucibleLiftRatio',
    'Crucible position':          'cruciblePosition',
    'Crucible Position Calibrated': 'cruciblePosCalibrated',
    'CTPFL_PUL':                  'ctpflPul',
    'MAGNET PV':                  'magnetPv',
    'Argon gas flow rate':        'argonFlowRate',
    'Lower chamber press':        'lowerChamberPress',
    'Lower chamber press SP':     'lowerChamberPressSp',
    'Thro Valve Open':            'throValveOpen',
    'BPmean':                     'bpMean',
    'BPU60mean':                  'bpu60mean',
    'BTPL_BPUL1':                 'btplBpul1',
    'BTPL_BPLL1':                 'btplBpll1',
    'PIDSL_dDmean':               'pidslDdmean',
    'PIDSL_temp1':                'pidslTemp1',
    'Residual Weight':            'residualWeight',
    'Seed rotation SP':           'seedRotationSp',
    'countb':                     'countb',
}

# 不匯入 MQTT 的欄位
SKIP_COLS = {'DatabaseName', 'EventStartTime', 'EventEndTime'}

# 數值欄位（需 float 轉換）
NUMERIC_FIELDS = {
    'diameter', 'dMean', 'diameterTarget',
    'heaterTemp', 'heaterTempTarget', 'heaterPowerSv', 'htMean',
    'grMean', 'temp2', 'temp4', 'temp5', 'temp9', 'temp29',
    'bodyLength', 'neckLengthAccum',
    'seedLift', 'seedLiftSp', 'seedLiftTarget',
    'crucibleRotationSp', 'crMean', 'crucibleLift',
    'crucibleLiftRatio', 'cruciblePosition', 'cruciblePosCalibrated',
    'ctpflPul', 'magnetPv',
    'argonFlowRate', 'lowerChamberPress', 'lowerChamberPressSp',
    'throValveOpen', 'bpMean', 'bpu60mean', 'btplBpul1', 'btplBpll1',
    'pidslDdmean', 'pidslTemp1',
    'residualWeight', 'seedRotationSp', 'countb',
}


def row_to_payload(row: dict) -> dict:
    """
    CSV 一列 (dict) → MQTT JSON payload (dict)
    furnace_id 直接從 PULLER 欄位取得，不需外部傳入。
    """
    payload = {}

    for csv_key, json_key in FIELD_MAP.items():
        if csv_key in SKIP_COLS:
            continue
        val = row.get(csv_key, '')
        if val == '' or val is None:
            payload[json_key] = None
            continue

        if json_key in ('logTime', 'ingotNo', 'furnaceId', 'operationMode', 'sop'):
            payload[json_key] = str(val).strip()
        elif json_key in NUMERIC_FIELDS:
            try:
                payload[json_key] = float(val)
            except (ValueError, TypeError):
                payload[json_key] = None
        else:
            payload[json_key] = val

    payload['receivedAt'] = datetime.utcnow().isoformat() + 'Z'
    return payload


def get_furnace_id(row: dict) -> str:
    """從 CSV row 取得爐子 ID（PULLER 欄位）"""
    return str(row.get('PULLER', 'UNKNOWN')).strip()


def to_json(payload: dict) -> str:
    return json.dumps(payload, ensure_ascii=False)
