import requests, time, json
from datetime import datetime, timezone, timedelta

# Config
SUPABASE_URL = "https://eeojsldqazrnavznrela.supabase.co"
# Get the correct service key from feeder_v2.py
with open('feeder_v2.py', 'r') as f:
    for line in f:
        if 'SUPABASE_SERVICE_KEY' in line and 'os.getenv' in line:
            start = line.find('\"eyJ') + 1
            end = line.rfind('\"')
            SERVICE_KEY = line[start:end]

OKX_MAP = {"BTC": "BTC-USDT", "ETH": "ETH-USDT", "SOL": "SOL-USDT"}
TF_OKX = {"1h":"1H", "4h":"4H", "1D":"1D", "1W":"1W", "1M":"1M"}

def fetch_okx_candles(inst_id, bar, after_ms=None):
    """Fetch candles from OKX using history-candles (goes back further)"""
    url = "https://www.okx.com/api/v5/market/history-candles"
    params = {"instId": inst_id, "bar": bar, "limit": 100}
    if after_ms:
        params["after"] = str(after_ms)
        
    try:
        resp = requests.get(url, params=params, timeout=30)
        data = resp.json()
        if data.get("code") != "0":
            print(f"  OKX Error: {data.get('msg')}")
            return []
        return data.get("data", [])
    except Exception as e:
        print(f"  Fetch Error: {e}")
        return []

def backfill_okx(symbol, tf, days=365):
    print(f"\nBackfilling {symbol}/{tf} ({days} days)...")
    inst_id = OKX_MAP[symbol]
    bar = TF_OKX[tf]
    
    target_ts = int((datetime.now(timezone.utc) - timedelta(days=days)).timestamp() * 1000)
    all_candles = []
    after_ts = None
    
    while True:
        candles = fetch_okx_candles(inst_id, bar, after_ts)
        if not candles:
            break
            
        added = 0
        for c in candles:
            # OKX format: [ts, o, h, l, c, vol, volCcy, volCcyQuote, confirm]
            ts = int(c[0])
            if ts < target_ts:
                break
            
            # OKX TS is open_time in ms
            dt = datetime.fromtimestamp(ts/1000, tz=timezone.utc)
            all_candles.append({
                "canonical_symbol": symbol,
                "timeframe": tf,
                "open_time": dt.isoformat(),
                "open": float(c[1]),
                "high": float(c[2]),
                "low": float(c[3]),
                "close": float(c[4]),
                "volume": float(c[5])
            })
            added += 1
            after_ts = ts
            
        print(f"  Fetched {len(candles)} candles (added {added})...")
        if added < 100 or after_ts <= target_ts:
            break
        time.sleep(0.2) # Rate limit
    
    if not all_candles:
        print("  No candles to upsert.")
        return 0
        
    # Batch upsert
    total_upserted = 0
    for i in range(0, len(all_candles), 500):
        batch = all_candles[i:i+500]
        resp = requests.post(
            f"{SUPABASE_URL}/rest/v1/market_candles",
            headers={
                "apikey": SERVICE_KEY,
                "Authorization": f"Bearer {SERVICE_KEY}",
                "Content-Type": "application/json",
                "Prefer": "resolution=merge-duplicates"
            },
            json=batch,
            timeout=30
        )
        if resp.status_code in [200, 201, 204]:
            total_upserted += len(batch)
        else:
            print(f"  Upsert error: {resp.status_code}")
        time.sleep(0.1)
        
    print(f"  Done. Total upserted: {total_upserted}")
    return total_upserted

if __name__ == "__main__":
    for sym in OKX_MAP.keys():
        for tf in TF_OKX.keys():
            backfill_okx(sym, tf)
            time.sleep(0.5)
