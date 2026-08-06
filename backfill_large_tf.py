import requests, time, json, sys
from datetime import datetime, timedelta, timezone

# Config
SUPABASE_URL = "https://eeojsldqazrnavznrela.supabase.co"
SERVICE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVlb2pzbGRxYXpybmF2em5yZWxhIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4NTYzMjkxMiwiZXhwIjoyMTAxMjA4OTEyfQ.Nff1aPBzpOLUcnEe7FYUwx5TACOBQCk-8oeTvzQGGro"
OANDA_TOKEN = "0dcfea75f50aa3b19d0e8c4810a865f4-e23a605e060d62d2d3e3011d1156f124"
OANDA_LIVE = "api-fxtrade.oanda.com"

# OANDA instrument mapping (canonical -> OANDA)
OANDA_MAP = {
    "XAUUSD": "XAU_USD", "XAGUSD": "XAG_USD", "WTI": "WTICO_USD", "BRENT": "BCO_USD",
    "EURUSD": "EUR_USD", "GBPUSD": "GBP_USD", "USDJPY": "USD_JPY", "AUDUSD": "AUD_USD",
    "USDCHF": "USD_CHF", "USDCAD": "USD_CAD", "NZDUSD": "NZD_USD",
    "EURGBP": "EUR_GBP", "EURJPY": "EUR_JPY", "GBPJPY": "GBP_JPY"
}

# OANDA granularity mapping (our TF -> OANDA gran)
TF_OANDA = {"1m":"M1","5m":"M5","15m":"M15","30m":"M30","1h":"H1","4h":"H4","1D":"D","1W":"W","1M":"M"}

# Max candles per OANDA request per granularity
MAX_PER_REQ = {"M1":5000,"M5":5000,"M15":5000,"M30":5000,"H1":5000,"H4":5000,"D":5000,"W":5000,"M":5000}

def get_last_trading_time():
    """Get last Friday 21:00 UTC (approximate market close)"""
    now = datetime.now(timezone.utc)
    # If weekend, go back to Friday
    days_back = (now.weekday() - 4) % 7
    if days_back == 0 and now.hour < 21:
        days_back = 7
    elif days_back == 0:
        days_back = 0
    friday = now - timedelta(days=days_back)
    return friday.replace(hour=21, minute=0, second=0, microsecond=0)

def fetch_oanda_candles(instrument, granularity, from_dt, to_dt):
    """Fetch candles from OANDA with pagination"""
    candles = []
    current_from = from_dt
    
    while current_from < to_dt:
        url = f"https://{OANDA_LIVE}/v3/instruments/{instrument}/candles"
        params = {
            "from": current_from.strftime("%Y-%m-%dT%H:%M:%S"),
            "to": to_dt.strftime("%Y-%m-%dT%H:%M:%S"),
            "granularity": granularity,
            "price": "MBA"
        }
        headers = {"Authorization": f"Bearer {OANDA_TOKEN}"}
        
        try:
            resp = requests.get(url, headers=headers, params=params, timeout=30)
            data = resp.json()
            
            if "candles" not in data or len(data["candles"]) == 0:
                break
            
            for c in data["candles"]:
                if c["complete"] or c.get("volume", 0) > 0:
                    mid = c.get("mid", {})
                    o = float(mid.get("o", 0))
                    h = float(mid.get("h", 0))
                    l = float(mid.get("l", 0))
                    cl = float(mid.get("c", 0))
                    
                    if o > 0 and h > 0 and l > 0 and cl > 0:
                        open_time = datetime.fromisoformat(c["time"].replace("Z", "+00:00"))
                        candles.append({
                            "open_time": open_time.isoformat(),
                            "open": o, "high": h, "low": l, "close": cl,
                            "volume": c.get("volume", 0)
                        })
            
            last_time = datetime.fromisoformat(data["candles"][-1]["time"].replace("Z", "+00:00"))
            # Move forward by 1 candle period
            tf_seconds = {"M1":60,"M5":300,"M15":900,"M30":1800,"H1":3600,"H4":14400,"D":86400,"W":604800,"M":2592000}
            current_from = last_time + timedelta(seconds=tf_seconds.get(granularity, 3600))
            
            if len(data["candles"]) < 10:
                break
                
            time.sleep(0.15)  # Rate limit
            
        except Exception as e:
            print(f"Error: {e}")
            time.sleep(1)
            break
    
    return candles

def upsert_candles(symbol, tf, candles):
    """Upsert candles to Supabase"""
    if not candles:
        return 0
    
    rows = []
    for c in candles:
        rows.append({
            "canonical_symbol": symbol,
            "timeframe": tf,
            "open_time": c["open_time"],
            "open": c["open"],
            "high": c["high"],
            "low": c["low"],
            "close": c["close"],
            "volume": c["volume"]
        })
    
    total_upserted = 0
    for i in range(0, len(rows), 500):
        batch = rows[i:i+500]
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
            print(f"Upsert error: {resp.status_code} {resp.text[:200]}")
        
        time.sleep(0.1)
    
    return total_upserted

def verify_count(symbol, tf):
    """Verify candle count in DB"""
    resp = requests.get(
        f"{SUPABASE_URL}/rest/v1/market_candles?canonical_symbol=eq.{symbol}&timeframe=eq.{tf}&select=open_time&order=open_time.asc&limit=1",
        headers={"apikey": SERVICE_KEY, "Authorization": f"Bearer {SERVICE_KEY}"},
        timeout=30
    )
    first = resp.json()
    
    resp2 = requests.get(
        f"{SUPABASE_URL}/rest/v1/market_candles?canonical_symbol=eq.{symbol}&timeframe=eq.{tf}&select=open_time&order=open_time.desc&limit=1",
        headers={"apikey": SERVICE_KEY, "Authorization": f"Bearer {SERVICE_KEY}"},
        timeout=30
    )
    last = resp2.json()
    
    resp3 = requests.head(
        f"{SUPABASE_URL}/rest/v1/market_candles?canonical_symbol=eq.{symbol}&timeframe=eq.{tf}",
        headers={"apikey": SERVICE_KEY, "Authorization": f"Bearer {SERVICE_KEY}", "Prefer": "count=exact"},
        timeout=30
    )
    count = resp3.headers.get("content-range", "").split("/")[-1] if "/" in resp3.headers.get("content-range", "") else "?"
    
    print(f"  Count: {count}")
    if first:
        print(f"  Oldest: {first[0]['open_time']}")
    if last:
        print(f"  Newest: {last[0]['open_time']}")
    
    return count

def backfill_symbol_tf(symbol, tf, days=365):
    """Backfill one symbol/timeframe"""
    print(f"\n{'='*50}")
    print(f"Backfilling {symbol}/{tf} ({days} days)")
    print(f"{'='*50}")
    
    to_time = get_last_trading_time()
    from_time = to_time - timedelta(days=days)
    
    oanda_inst = OANDA_MAP.get(symbol)
    if not oanda_inst:
        print(f"Not an OANDA symbol: {symbol}")
        return 0
    
    gran = TF_OANDA.get(tf)
    if not gran:
        print(f"Unknown timeframe: {tf}")
        return 0
    
    print(f"OANDA: {oanda_inst} / {gran}")
    print(f"Range: {from_time.date()} → {to_time.date()}")
    
    candles = fetch_oanda_candles(oanda_inst, gran, from_time, to_time)
    print(f"Fetched {len(candles)} candles")
    
    if not candles:
        print("No candles fetched")
        return 0
    
    upserted = upsert_candles(symbol, tf, candles)
    print(f"Upserted {upserted} candles")
    
    count = verify_count(symbol, tf)
    return count

# Main
if __name__ == "__main__":
    # Start with XAUUSD/4h
    backfill_symbol_tf("XAUUSD", "4h", days=365)
