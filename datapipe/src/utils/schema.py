import json
from datetime import datetime

FIELD_MAP = {
    'LogTime':'logTime','INGOT_NO':'ingotNo','Event':'event',
    'Operation Mode':'operationMode','SOP':'sop',
    'Diameter':'diameter','D_mean':'dMean','Diameter target':'diameterTarget',
    'Heater temp':'heaterTemp','Heater temp target':'heaterTempTarget',
    'Heater Power SV':'heaterPowerSv','HT_mean':'htMean','GR_mean':'grMean',
    'Seed lift':'seedLift','Seed lift SP':'seedLiftSp','Seed lift target':'seedLiftTarget',
    'Body length':'bodyLength','Neck length Accum':'neckLengthAccum',
    'Magnet PV':'magnetPv','Temp2':'temp2','Temp4':'temp4','Temp5':'temp5','Temp29':'temp29',
    'Crucible Rotation SP':'crucibleRotationSp','CR_mean':'crMean',
    'Residual Weight':'residualWeight','BP_mean':'bpMean',
    'BPU60mean':'bpu60mean','PIDSL_DDmean':'pidslDdmean','PIDSL_Temp1':'pidslTemp1',
}

def row_to_payload(row, furnace_id):
    payload = {'furnaceId': furnace_id}
    for csv_key, json_key in FIELD_MAP.items():
        val = row.get(csv_key, '')
        if val == '' or val is None:
            payload[json_key] = None
            continue
        if json_key == 'event':
            try: payload[json_key] = int(float(val))
            except: payload[json_key] = 1
        elif json_key in ('logTime','ingotNo','operationMode','sop'):
            payload[json_key] = str(val).strip()
        else:
            try: payload[json_key] = float(val)
            except: payload[json_key] = None
    payload['receivedAt'] = datetime.utcnow().isoformat() + 'Z'
    return payload

def to_json(payload):
    return json.dumps(payload, ensure_ascii=False)
