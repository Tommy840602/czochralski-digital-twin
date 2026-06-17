"""
長晶爐數位孿生 — CSV 批次匯入腳本
用法：
  # 匯入單一檔案
  python import_csv.py --file data/D1_all.csv

  # 匯入整個目錄（自動掃描所有 *_all.csv）
  python import_csv.py --dir data/

  # 指定資料庫連線
  python import_csv.py --dir data/ --url postgresql://twin:twin_secret@localhost:5433/furnace_db

  # 乾跑（不寫入DB，只驗證）
  python import_csv.py --dir data/ --dry-run

特色：
  - 自動從 CSV 的 PULLER 欄位識別爐子 ID
  - 自動 INSERT INTO furnace_registry（衝突忽略），不需預先設定爐子清單
  - 批次寫入（預設 5000 筆/批），記憶體友善
  - 斷點續傳：已存在的 (furnace_id, time) 用 ON CONFLICT DO NOTHING
  - 進度條顯示
  - 移除 DatabaseName / EventStartTime / EventEndTime 三欄
"""

import argparse
import sys
import time
from pathlib import Path
from datetime import datetime

import pandas as pd
import psycopg2
import psycopg2.extras

# ── 欄位對應表：CSV 欄名 → DB 欄名 ──────────────────────────────────────
COL_MAP = {
    'INGOT_NO':                   'ingot_no',
    'Operation Mode':             'operation_mode',
    'SOP':                        'sop',
    'Diameter':                   'diameter',
    'D_mean':                     'd_mean',
    'Diameter target':            'diameter_target',
    'Heater temp':                'heater_temp',
    'Heater temp target':         'heater_temp_target',
    'Heater Power SV':            'heater_power_sv',
    'HTmean':                     'ht_mean',
    'temp2':                      'temp2',
    'temp4':                      'temp4',
    'temp5':                      'temp5',
    'temp9':                      'temp9',
    'temp29':                     'temp29',
    'GR_mean':                    'gr_mean',
    'Body length':                'body_length',
    'Neck Length Accum':          'neck_length_accum',
    'Seed lift':                  'seed_lift',
    'Seed Lift SP':               'seed_lift_sp',
    'Seed lift target':           'seed_lift_target',
    'Crucible rotation SP':       'crucible_rotation_sp',
    'CRmean':                     'cr_mean',
    'Crucible Lift':              'crucible_lift',
    'Crucible lift ratio':        'crucible_lift_ratio',
    'Crucible position':          'crucible_position',
    'Crucible Position Calibrated': 'crucible_pos_calibrated',
    'CTPFL_PUL':                  'ctpfl_pul',
    'MAGNET PV':                  'magnet_pv',
    'Argon gas flow rate':        'argon_flow_rate',
    'Lower chamber press':        'lower_chamber_press',
    'Lower chamber press SP':     'lower_chamber_press_sp',
    'Thro Valve Open':            'thro_valve_open',
    'BPmean':                     'bp_mean',
    'BPU60mean':                  'bpu60mean',
    'BTPL_BPUL1':                 'btpl_bpul1',
    'BTPL_BPLL1':                 'btpl_bpll1',
    'PIDSL_dDmean':               'pidsl_ddmean',
    'PIDSL_temp1':                'pidsl_temp1',
    'Residual Weight':            'residual_weight',
    'Seed rotation SP':           'seed_rotation_sp',
    'countb':                     'countb',
}

DROP_COLS = {'DatabaseName', 'EventStartTime', 'EventEndTime'}

DB_COLS = ['time', 'furnace_id'] + list(COL_MAP.values())

INSERT_SQL = f"""
INSERT INTO furnace_metrics ({', '.join(DB_COLS)})
VALUES %s
ON CONFLICT DO NOTHING
"""

REGISTRY_SQL = """
               INSERT INTO furnace_registry (furnace_id, display_name, status)
               VALUES (%s, %s, 'idle')
                   ON CONFLICT (furnace_id) DO NOTHING \
               """


def connect(url: str):
    conn = psycopg2.connect(url)
    conn.autocommit = False
    return conn


def ensure_registry(conn, furnace_id: str):
    """自動註冊爐子，衝突忽略"""
    with conn.cursor() as cur:
        cur.execute(REGISTRY_SQL, (furnace_id, f'長晶爐 {furnace_id}'))
    conn.commit()
    print(f'  [registry] {furnace_id} 已確認存在')


def import_file(conn, csv_path: Path, batch_size: int, dry_run: bool) -> dict:
    """匯入單一 CSV 檔案，回傳統計資訊"""
    print(f'\n{"="*60}')
    print(f'匯入: {csv_path.name}')
    print(f'{"="*60}')

    t0 = time.time()

    # 讀取 CSV（BOM 安全）
    df = pd.read_csv(csv_path, encoding='utf-8-sig')
    total_rows = len(df)
    print(f'  讀取完成: {total_rows:,} 筆')

    # 移除不需要的欄位
    df = df.drop(columns=[c for c in DROP_COLS if c in df.columns])

    # 取得爐子 ID
    furnace_ids = df['PULLER'].unique().tolist()
    print(f'  PULLER: {furnace_ids}')

    if not dry_run:
        for fid in furnace_ids:
            ensure_registry(conn, fid)

    # 轉換 LogTime
    df['LogTime'] = pd.to_datetime(df['LogTime'], errors='coerce')
    bad_time = df['LogTime'].isna().sum()
    if bad_time > 0:
        print(f'  ⚠ 無效 LogTime: {bad_time} 筆（已跳過）')
    df = df.dropna(subset=['LogTime'])

    stats = {'file': csv_path.name, 'total': total_rows, 'inserted': 0,
             'skipped': bad_time, 'batches': 0, 'furnaces': furnace_ids}

    # 批次寫入
    inserted = 0
    batches = 0
    rows_iter = range(0, len(df), batch_size)

    for start in rows_iter:
        chunk = df.iloc[start:start + batch_size]
        records = []

        for _, row in chunk.iterrows():
            vals = [row['LogTime'].to_pydatetime(), row['PULLER']]
            for csv_col, db_col in COL_MAP.items():
                val = row.get(csv_col, None)
                # 把 NaN 轉成 None
                if pd.isna(val):
                    val = None
                vals.append(val)
            records.append(tuple(vals))

        if not dry_run:
            with conn.cursor() as cur:
                psycopg2.extras.execute_values(cur, INSERT_SQL, records, page_size=batch_size)
            conn.commit()

        inserted += len(records)
        batches += 1
        pct = min(100, int((start + batch_size) / len(df) * 100))
        bar = '█' * (pct // 5) + '░' * (20 - pct // 5)
        print(f'  [{bar}] {pct:3d}%  {inserted:,}/{len(df):,} 筆', end='\r')

    print()  # newline after progress bar

    elapsed = time.time() - t0
    stats['inserted'] = inserted
    stats['batches'] = batches
    stats['elapsed'] = elapsed

    print(f'  ✅ 完成  {inserted:,} 筆  耗時 {elapsed:.1f}s  '
          f'({inserted/elapsed:.0f} rows/s)')
    return stats


def print_summary(all_stats: list):
    print(f'\n{"="*60}')
    print('匯入總結')
    print(f'{"="*60}')
    total = sum(s['total'] for s in all_stats)
    inserted = sum(s['inserted'] for s in all_stats)
    elapsed = sum(s['elapsed'] for s in all_stats)
    furnaces = set()
    for s in all_stats:
        furnaces.update(s['furnaces'])

    for s in all_stats:
        status = '✅' if s['inserted'] > 0 else '⚠'
        print(f"  {status} {s['file']:<20} {s['inserted']:>8,} 筆  "
              f"{s['elapsed']:.1f}s")

    print(f'\n  爐子清單: {sorted(furnaces)}')
    print(f'  總筆數:   {total:,}')
    print(f'  已寫入:   {inserted:,}')
    print(f'  總耗時:   {elapsed:.1f}s')
    print(f'  平均速率: {inserted/elapsed:.0f} rows/s')


def main():
    parser = argparse.ArgumentParser(description='長晶爐 CSV → TimescaleDB 匯入工具')
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--file', help='匯入單一 CSV 檔案')
    group.add_argument('--dir',  help='匯入目錄內所有 *_all.csv')

    parser.add_argument(
        '--url',
        default='postgresql://twin:twin_secret@localhost:5433/furnace_db',
        help='TimescaleDB 連線字串（預設 localhost:5433）'
    )
    parser.add_argument('--batch-size', type=int, default=5000,
                        help='每批寫入筆數（預設 5000）')
    parser.add_argument('--dry-run', action='store_true',
                        help='只驗證不寫入')

    args = parser.parse_args()

    # 收集檔案清單
    if args.file:
        files = [Path(args.file)]
    else:
        d = Path(args.dir)
        files = sorted(d.glob('*_all.csv'))
        if not files:
            print(f'❌ 在 {d} 找不到 *_all.csv 檔案')
            sys.exit(1)

    print(f'長晶爐 CSV 匯入工具')
    print(f'目標 DB: {args.url}')
    print(f'批次大小: {args.batch_size:,}')
    print(f'乾跑模式: {args.dry_run}')
    print(f'檔案數量: {len(files)}')

    if args.dry_run:
        print('\n⚠ 乾跑模式：不會寫入任何資料')
        conn = None
    else:
        print('\n連線資料庫...')
        try:
            conn = connect(args.url)
            print('✅ 連線成功')
        except Exception as e:
            print(f'❌ 連線失敗: {e}')
            sys.exit(1)

    all_stats = []
    try:
        for f in files:
            stats = import_file(conn, f, args.batch_size, args.dry_run)
            all_stats.append(stats)
    finally:
        if conn:
            conn.close()

    print_summary(all_stats)


if __name__ == '__main__':
    main()