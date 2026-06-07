import csv
from pathlib import Path
from src.utils.logger import get_logger

log = get_logger('simulator.csv_reader')

def read_csv(path):
    p = Path(path)
    if not p.exists():
        raise FileNotFoundError(f'CSV 不存在: {path}')
    rows = []
    with open(p, encoding='utf-8-sig') as f:
        for row in csv.DictReader(f):
            rows.append(dict(row))
    log.info(f'載入 {len(rows)} 筆：{p.name}')
    return rows
