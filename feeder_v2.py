#!/usr/bin/env python3
"""
TradeVision Data Worker v2
- Fetches candles from OANDA (Forex/Metals/Energy) and OKX (Crypto)
- Validates & Upserts to Supabase (market_candles + market_prices)
- Polling with adaptive rate limiting for 17 symbols x 9 timeframes
- Uses service_role key for writes
"""

import asyncio
import aiohttp
import os
import logging
import random
from datetime import datetime, timezone, timedelta
from typing import Dict, List, Optional
from dataclasses import dataclass
from supabase import create_client, Client

# ============================================================
# CONFIGURATION
# ============================================================

SUPABASE_URL = "https://eeojsldqazrnavznrela.supabase.co"
SUPABASE_SERVICE_KEY = os.getenv("SUPABASE_SERVICE_KEY", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVlb2pzbGRxYXpybmF2em5yZWxhIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4NTYzMjkxMiwiZXhwIjoyMTAxMjA4OTEyfQ.Nff1aPBzpOLUcnEe7FYUwx5TACOBQCk-8oeTvzQGGro")

OANDA_ACCOUNT_ID = "001-001-21958739-001"
OANDA_TOKEN = os.getenv("OANDA_TOKEN", "")
OKX_BASE = "https://www.okx.com"

SYMBOL_CONFIG = {
    "XAUUSD": {"oanda": "XAU_USD", "okx": None, "category": "METALS"},
    "XAGUSD": {"oanda": "XAG_USD", "okx": None, "category": "METALS"},
    "WTI":    {"oanda": "WTICO_USD", "okx": None, "category": "ENERGY"},
    "BRENT":  {"oanda": "BCO_USD", "okx": None, "category": "ENERGY"},
    "EURUSD": {"oanda": "EUR_USD", "okx": None, "category": "FOREX"},
    "GBPUSD": {"oanda": "GBP_USD", "okx": None, "category": "FOREX"},
    "USDJPY": {"oanda": "USD_JPY", "okx": None, "category": "FOREX"},
    "AUDUSD": {"oanda": "AUD_USD", "okx": None, "category": "FOREX"},
    "USDCAD": {"oanda": "USD_CAD", "okx": None, "category": "FOREX"},
    "NZDUSD": {"oanda": "NZD_USD", "okx": None, "category": "FOREX"},
    "USDCHF": {"oanda": "USD_CHF", "okx": None, "category": "FOREX"},
    "EURGBP": {"oanda": "EUR_GBP", "okx": None, "category": "FOREX"},
    "EURJPY": {"oanda": "EUR_JPY", "okx": None, "category": "FOREX"},
    "GBPJPY": {"oanda": "GBP_JPY", "okx": None, "category": "FOREX"},
    "BTC":    {"oanda": None, "okx": "BTC-USDT", "category": "CRYPTO"},
    "ETH":    {"oanda": None, "okx": "ETH-USDT", "category": "CRYPTO"},
    "SOL":    {"oanda": None, "okx": "SOL-USDT", "category": "CRYPTO"},
}

TF_OANDA = {
    "1m": "M1", "5m": "M5", "15m": "M15", "30m": "M30",
    "1h": "H1", "4h": "H4", "1d": "D", "1W": "W", "1M": "M"
}
TF_OKX = {
    "1m": "1m", "5m": "5m", "15m": "15m", "30m": "30m",
    "1h": "1H", "4h": "4H", "1d": "1D", "1W": "1W", "1M": "1M"
}

POLL_INTERVALS = {
    "1m": 2.0, "5m": 5.0, "15m": 10.0, "30m": 15.0,
    "1h": 30.0, "4h": 60.0, "1d": 300.0, "1W": 1800.0, "1M": 3600.0,
}

OANDA_SEMAPHORE = asyncio.Semaphore(30)

# ============================================================
# PHASE 2.6: OKX Rate Limit Stabilization
# ============================================================

# 1) Global Semaphore for ALL OKX requests (max 10 concurrent)
OKX_SEMAPHORE = asyncio.Semaphore(10)

# 2) Request Deduplication: track in-flight requests per (symbol, timeframe)
_okx_in_progress: dict[tuple[str, str], asyncio.Task] = {}

# 3) Cache with TTL per timeframe
_okx_cache: dict[tuple[str, str], tuple[datetime, list]] = {}  # key: (symbol, tf) -> (timestamp, data)

def _cache_ttl(tf: str) -> float:
    """TTL based on poll interval for each timeframe"""
    return POLL_INTERVALS.get(tf, 2.0) * 0.8  # 80% of poll interval

def _cache_get(symbol: str, tf: str) -> Optional[list]:
    """Get cached data if fresh"""
    key = (symbol, tf)
    if key in _okx_cache:
        cached_time, data = _okx_cache[key]
        if (datetime.now(timezone.utc) - cached_time).total_seconds() < _cache_ttl(tf):
            log.debug(f"[OKX CACHE HIT] {symbol} {tf}")
            return data
    return None

def _cache_set(symbol: str, tf: str, data: list):
    """Store data in cache"""
    _okx_cache[(symbol, tf)] = (datetime.now(timezone.utc), data)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(message)s",
    datefmt="%H:%M:%S"
)
log = logging.getLogger("feeder")


@dataclass
class Candle:
    canonical_symbol: str
    timeframe: str
    open_time: datetime
    open: float
    high: float
    low: float
    close: float
    volume: float
    is_closed: bool
    source: str = "feeder"


supabase: Client = create_client(SUPABASE_URL, SUPABASE_SERVICE_KEY)


def validate_candle(c: Candle) -> tuple[bool, str]:
    if c.open <= 0 or c.high <= 0 or c.low <= 0 or c.close <= 0:
        return False, f"Non-positive price: O={c.open} H={c.high} L={c.low} C={c.close}"
    if c.high < max(c.open, c.close) or c.low > min(c.open, c.close):
        return False, f"High/Low invalid: H={c.high} L={c.low} O={c.open} C={c.close}"
    if c.high < c.low:
        return False, f"High < Low: {c.high} < {c.low}"
    if c.volume < 0:
        return False, f"Negative volume: {c.volume}"
    tf_seconds = tf_to_seconds(c.timeframe)
    if tf_seconds and int(c.open_time.timestamp()) % tf_seconds != 0:
        return False, f"Timestamp not aligned to {c.timeframe}: {c.open_time}"
    return True, ""


def tf_to_seconds(tf: str) -> Optional[int]:
    mapping = {
        "1m": 60, "5m": 300, "15m": 900, "30m": 1800,
        "1h": 3600, "4h": 14400, "1d": 86400, "1W": 604800
    }
    return mapping.get(tf)


async def fetch_oanda_candles(
    session: aiohttp.ClientSession,
    instrument: str,
    granularity: str,
    from_time: datetime,
    to_time: datetime
) -> List[Dict]:
    url = f"https://api-fxtrade.oanda.com/v3/instruments/{instrument}/candles"
    params = {
        "granularity": granularity,
        "from": from_time.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "to": to_time.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "price": "M",
        "alignmentTimezone": "UTC",
        "dailyAlignment": 0,
    }
    headers = {"Authorization": f"Bearer {OANDA_TOKEN}"}

    async with OANDA_SEMAPHORE:
        for attempt in range(3):  # Max 3 retries
            try:
                async with session.get(url, params=params, headers=headers) as resp:
                    if resp.status == 429:
                        wait = (2 ** attempt) * 5  # 5s, 10s, 20s
                        log.warning(f"OANDA 429 for {instrument} {granularity}, backing off {wait}s (attempt {attempt+1}/3)")
                        await asyncio.sleep(wait)
                        continue
                    if resp.status != 200:
                        text = await resp.text()
                        log.error(f"OANDA {resp.status} for {instrument}: {text[:200]}")
                        return []
                    data = await resp.json()
                    return data.get("candles", [])
            except Exception as e:
                if attempt == 2:
                    log.error(f"OANDA fetch error {instrument} after 3 attempts: {e}")
                    return []
                await asyncio.sleep(2 ** attempt)
        return []


def parse_oanda_candle(raw: Dict, canonical: str, tf: str) -> Optional[Candle]:
    if not raw.get("complete", False):
        return None
    mid = raw.get("mid", {})
    try:
        return Candle(
            canonical_symbol=canonical,
            timeframe=tf,
            open_time=datetime.fromisoformat(raw["time"].replace("Z", "+00:00")),
            open=float(mid["o"]),
            high=float(mid["h"]),
            low=float(mid["l"]),
            close=float(mid["c"]),
            volume=float(raw.get("volume", 0)),
            is_closed=True,
            source="oanda"
        )
    except Exception as e:
        log.error(f"Parse OANDA error: {e}")
        return None


async def fetch_okx_candles(
    session: aiohttp.ClientSession,
    inst_id: str,
    bar: str,
    after: int,
    before: int,
    symbol: str = "",
    tf: str = "",
    from_ms: int = 0
) -> List[Dict]:
    """
    Fetch candles from OKX with pagination.
    Uses after cursor to paginate backwards from newest to oldest.
    """
    url = f"{OKX_BASE}/api/v5/market/candles"
    limit = 100

    # 1) Check cache first
    if symbol and tf:
        cached = _cache_get(symbol, tf)
        if cached is not None:
            return cached

    all_candles = []

    # 2) Global semaphore for ALL OKX requests (async with = auto release on exception)
    async with OKX_SEMAPHORE:
        # First, fetch the latest candles without after cursor
        params = {"instId": inst_id, "bar": bar, "limit": str(limit)}
        for attempt in range(3):
            try:
                async with session.get(url, params=params) as resp:
                    if resp.status == 429:
                        wait = [5, 15, 60][attempt]
                        log.warning(f"[OKX 429] {inst_id} {bar} | retry {attempt+1}/3 | wait {wait}s")
                        await asyncio.sleep(wait)
                        continue
                    if resp.status != 200:
                        text = await resp.text()
                        log.error(f"[OKX ERROR] {inst_id} {bar} | HTTP {resp.status} | {text[:200]}")
                        return []
                    data = await resp.json()
                    if data.get("code") != "0":
                        log.error(f"[OKX API ERROR] {inst_id} {bar} | {data}")
                        return []
                    result = data.get("data", [])
                    if not result:
                        return []
                    all_candles.extend(result)
                    break
            except Exception as e:
                if attempt == 2:
                    log.error(f"[OKX FETCH FAILED] {inst_id} {bar} after 3 attempts: {e}")
                    return []
                await asyncio.sleep(2 ** attempt)

        # Then paginate backwards using after cursor
        # after cursor = oldest candle timestamp - 1
        for page in range(20):  # Max 20 pages (2000 candles)
            oldest_ts = all_candles[-1][0]
            current_after = str(int(oldest_ts) - 1)

            # Stop if we've gone past the requested time range
            # 30 days ago = from_ms
            if int(oldest_ts) < from_ms:
                logging.info(f'[DEBUG] Reached start date - STOPPING. oldest={oldest_ts}, from={from_ms}')
                break

            params = {"instId": inst_id, "bar": bar, "after": current_after, "limit": str(limit)}
            for attempt in range(3):
                try:
                    async with session.get(url, params=params) as resp:
                        if resp.status == 429:
                            wait = [5, 15, 60][attempt]
                            log.warning(f"[OKX 429] {inst_id} {bar} | retry {attempt+1}/3 | wait {wait}s")
                            await asyncio.sleep(wait)
                            continue
                        if resp.status != 200:
                            text = await resp.text()
                            log.error(f"[OKX ERROR] {inst_id} {bar} | HTTP {resp.status} | {text[:200]}")
                            break
                        data = await resp.json()
                        if data.get("code") != "0":
                            log.error(f"[OKX API ERROR] {inst_id} {bar} | {data}")
                            break
                        result = data.get("data", [])
                        if not result:
                            break
                        all_candles.extend(result)
                        break
                except Exception as e:
                    if attempt == 2:
                        log.error(f"[OKX FETCH FAILED] {inst_id} {bar} after 3 attempts: {e}")
                    break

    # 3) Store in cache
    if symbol and tf and all_candles:
        _cache_set(symbol, tf, all_candles)

    return all_candles


def parse_okx_candle(raw: List, canonical: str, tf: str) -> Optional[Candle]:
    try:
        ts = int(raw[0])
        return Candle(
            canonical_symbol=canonical,
            timeframe=tf,
            open_time=datetime.fromtimestamp(ts / 1000, tz=timezone.utc),
            open=float(raw[1]),
            high=float(raw[2]),
            low=float(raw[3]),
            close=float(raw[4]),
            volume=float(raw[5]),
            is_closed=True,
            source="okx"
        )
    except Exception as e:
        log.error(f"Parse OKX error: {e}")
        return None


async def upsert_candles(candles: List[Candle]) -> int:
    if not candles:
        return 0
    rows = []
    for c in candles:
        valid, err = validate_candle(c)
        if not valid:
            log.warning(f"Invalid candle skipped: {c.canonical_symbol} {c.timeframe} {c.open_time} - {err}")
            continue
        rows.append({
            "canonical_symbol": c.canonical_symbol,
            "timeframe": c.timeframe,
            "open_time": c.open_time.isoformat(),
            "open": c.open,
            "high": c.high,
            "low": c.low,
            "close": c.close,
            "volume": c.volume,
            "is_closed": c.is_closed,
            "source": c.source
        })
    if not rows:
        return 0
    try:
        result = supabase.table("market_candles").upsert(rows, on_conflict="canonical_symbol,timeframe,open_time").execute()
        return len(result.data) if result.data else 0
    except Exception as e:
        log.error(f"Supabase upsert error: {e}")
        return 0


async def upsert_price(c: Candle) -> bool:
    mid = (c.high + c.low) / 2
    try:
        current = supabase.table("market_prices").select("mid_price").eq("canonical_symbol", c.canonical_symbol).execute()
        prev_mid = current.data[0]["mid_price"] if current.data else mid
        change_pct = ((mid - prev_mid) / prev_mid * 100) if prev_mid else 0

        supabase.table("market_prices").upsert({
            "canonical_symbol": c.canonical_symbol,
            "mid_price": mid,
            "bid_price": c.low,
            "ask_price": c.high,
            "prev_mid_price": prev_mid,
            "change_percent": round(change_pct, 4),
            "updated_at": datetime.now(timezone.utc).isoformat()
        }).execute()
        return True
    except Exception as e:
        log.error(f"Price upsert error {c.canonical_symbol}: {e}")
        return False


async def fetch_all_prices(session: aiohttp.ClientSession):
    """
    Periodically fetch prices for ALL symbols from OANDA and OKX,
    and update market_prices table. This runs independently of candle updates.
    """
    log.info("[PRICES] Starting price fetcher for all symbols")
    while True:
        for canonical, config in SYMBOL_CONFIG.items():
            try:
                if config["oanda"]:
                    # Fetch current price from OANDA pricing endpoint
                    instrument = config["oanda"]
                    url = f"https://api-fxtrade.oanda.com/v3/accounts/{OANDA_ACCOUNT_ID}/instruments/{instrument}/candles"
                    headers = {"Authorization": f"Bearer {OANDA_TOKEN}"}
                    params = {
                        "granularity": "M1",
                        "count": "1",
                        "price": "M"
                    }
                    async with session.get(url, headers=headers, params=params) as resp:
                        if resp.status == 200:
                            data = await resp.json()
                            candles = data.get("candles", [])
                            if candles:
                                c = candles[0]
                                mid_data = c.get("mid", {})
                                # OANDA mid has o, h, l, c — calculate mid as average of O and C
                                mid = (float(mid_data.get("o", 0)) + float(mid_data.get("c", 0))) / 2
                                bid_data = c.get("bid", {})
                                ask_data = c.get("ask", {})
                                bid = float(bid_data.get("b", mid)) if bid_data else mid
                                ask = float(ask_data.get("a", mid)) if ask_data else mid
                                supabase.table("market_prices").upsert({
                                    "canonical_symbol": canonical,
                                    "mid_price": mid,
                                    "bid_price": bid,
                                    "ask_price": ask,
                                    "updated_at": datetime.now(timezone.utc).isoformat()
                                }).execute()
                                log.info(f"[PRICES] OANDA {canonical}: mid={mid}")
                        elif resp.status == 429:
                            log.warning(f"[PRICES] OANDA 429 for {canonical}, skipping")
                        else:
                            text = await resp.text()
                            log.error(f"[PRICES] OANDA {resp.status} for {canonical}: {text[:100]}")

                if config["okx"]:
                    # Fetch current price from OKX
                    inst_id = config["okx"]
                    url = f"{OKX_BASE}/api/v5/market/ticker"
                    params = {"instId": inst_id}
                    async with session.get(url, params=params) as resp:
                        if resp.status == 200:
                            data = await resp.json()
                            if data.get("code") == "0" and data.get("data"):
                                ticker = data["data"][0]
                                mid = float(ticker.get("last", 0))
                                bid = float(ticker.get("bidPx", 0))
                                ask = float(ticker.get("askPx", 0))
                                supabase.table("market_prices").upsert({
                                    "canonical_symbol": canonical,
                                    "mid_price": mid,
                                    "bid_price": bid,
                                    "ask_price": ask,
                                    "updated_at": datetime.now(timezone.utc).isoformat()
                                }).execute()
                                log.info(f"[PRICES] OKX {canonical}: mid={mid}")
                        elif resp.status == 429:
                            log.warning(f"[PRICES] OKX 429 for {canonical}, skipping")
                        else:
                            log.error(f"[PRICES] OKX {resp.status} for {canonical}")

            except Exception as e:
                log.error(f"[PRICES] Error fetching {canonical}: {e}")

            await asyncio.sleep(0.3)  # Small delay between symbols

        # Wait 5 seconds before next price update cycle
        await asyncio.sleep(5)


async def poll_oanda_symbol(
    session: aiohttp.ClientSession,
    canonical: str,
    config: Dict,
    tf: str
):
    instrument = config["oanda"]
    granularity = TF_OANDA[tf]
    interval = POLL_INTERVALS[tf]
    task_start = datetime.now(timezone.utc).isoformat()
    log.info(f"[TASK START] OANDA {canonical} {tf} ({instrument}) at {task_start}")
    error_count = 0

    while True:
        try:
            to_time = datetime.now(timezone.utc)
            if tf in ["1m", "5m"]:
                from_time = to_time - timedelta(minutes=10)
            else:
                from_time = to_time - timedelta(seconds=7200)

            raw_candles = await fetch_oanda_candles(session, instrument, granularity, from_time, to_time)
            parsed = [parse_oanda_candle(r, canonical, tf) for r in raw_candles]
            valid_candles = [c for c in parsed if c]

            if valid_candles:
                upserted = await upsert_candles(valid_candles)
                if upserted:
                    latest = max(valid_candles, key=lambda x: x.open_time)
                    await upsert_price(latest)
                    log.info(f"[TASK SUCCESS] OANDA {canonical} {tf}: upserted {upserted} candles")
                    error_count = 0
            else:
                # No complete candles yet, but still update price from last data
                log.debug(f"[TASK SKIP] OANDA {canonical} {tf}: no complete candles this cycle")

        except Exception as e:
            error_count += 1
            log.error(f"[TASK ERROR] OANDA {canonical} {tf}: {e} (error #{error_count})")
            if error_count > 10:
                log.error(f"[TASK STOP] OANDA {canonical} {tf}: too many errors ({error_count}), stopping task")
                return

        await asyncio.sleep(interval)


async def poll_okx_symbol(
    session: aiohttp.ClientSession,
    canonical: str,
    config: Dict,
    tf: str
):
    inst_id = config["okx"]
    bar = TF_OKX[tf]
    interval = POLL_INTERVALS[tf]
    task_start = datetime.now(timezone.utc).isoformat()
    log.info(f"[TASK START] OKX {canonical} {tf} ({inst_id}) at {task_start}")
    error_count = 0

    # Start jitter: 0-2 seconds to prevent burst at startup
    await asyncio.sleep(random.uniform(0, 2))

    while True:
        try:
            to_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
            if tf in ["1m", "5m"]:
                from_ms = to_ms - (10 * 60 * 1000)
            else:
                from_ms = to_ms - (2 * 60 * 60 * 1000)

            raw_candles = await fetch_okx_candles(session, inst_id, bar, from_ms, to_ms, canonical, tf)
            parsed = [parse_okx_candle(r, canonical, tf) for r in raw_candles]
            valid_candles = [c for c in parsed if c]

            if valid_candles:
                upserted = await upsert_candles(valid_candles)
                if upserted:
                    latest = max(valid_candles, key=lambda x: x.open_time)
                    await upsert_price(latest)
                    log.info(f"[TASK SUCCESS] OKX {canonical} {tf}: upserted {upserted} candles")
                    error_count = 0
            else:
                log.debug(f"[TASK SKIP] OKX {canonical} {tf}: no complete candles this cycle")

        except Exception as e:
            error_count += 1
            log.error(f"[TASK ERROR] OKX {canonical} {tf}: {e} (error #{error_count})")
            if error_count > 10:
                log.error(f"[TASK STOP] OKX {canonical} {tf}: too many errors ({error_count}), stopping task")
                return

        await asyncio.sleep(interval)


async def backfill_historical(session: aiohttp.ClientSession):
    """
    Round-robin backfill: fetch a few candles from each symbol/TF,
    then move to the next. Prevents one symbol from blocking others.
    """
    log.info("Starting historical backfill (round-robin)...")

    # Build all symbol/TF pairs that need backfill
    pairs = []
    for canonical, config in SYMBOL_CONFIG.items():
        for tf in TF_OANDA.keys():
            if config["oanda"]:
                pairs.append(("oanda", canonical, config, tf))
            if config["okx"]:
                pairs.append(("okx", canonical, config, tf))

    log.info(f"[BACKFILL] Total pairs to backfill: {len(pairs)}")

    # Round-robin: process each pair one at a time
    for idx, (provider, canonical, config, tf) in enumerate(pairs):
        try:
            if provider == "oanda":
                instrument = config["oanda"]
                granularity = TF_OANDA[tf]
                to_time = datetime.now(timezone.utc)
                from_time = to_time - timedelta(days=30)
                raw = await fetch_oanda_candles(session, instrument, granularity, from_time, to_time)
                parsed = [parse_oanda_candle(r, canonical, tf) for r in raw]
                valid = [c for c in parsed if c]
                if valid:
                    await upsert_candles(valid)
                    log.info(f"[BACKFILL] OANDA {canonical} {tf}: {len(valid)} candles")

            elif provider == "okx":
                bar = TF_OKX[tf]
                to_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
                from_ms = to_ms - (30 * 24 * 60 * 60 * 1000)
                raw = await fetch_okx_candles(session, config["okx"], bar, from_ms, to_ms, canonical, tf)
                parsed = [parse_okx_candle(r, canonical, tf) for r in raw]
                valid = [c for c in parsed if c]
                if valid:
                    await upsert_candles(valid)
                    log.info(f"[BACKFILL] OKX {canonical} {tf}: {len(valid)} candles")

        except Exception as e:
            log.error(f"[BACKFILL] Error {provider} {canonical} {tf}: {e}")

        # Small delay between pairs to avoid rate limiting
        await asyncio.sleep(0.3)

    log.info("[BACKFILL] Historical backfill complete")


async def main():
    if not OANDA_TOKEN:
        log.error("OANDA_TOKEN not set in environment!")
        return

    log.info("=" * 50)
    log.info("TradeVision Feeder v2 Starting")
    oanda_count = sum(1 for v in SYMBOL_CONFIG.values() if v['oanda'])
    okx_count = sum(1 for v in SYMBOL_CONFIG.values() if v['okx'])
    total_tasks = oanda_count * len(TF_OANDA) + okx_count * len(TF_OKX)
    log.info(f"Symbols: {len(SYMBOL_CONFIG)} | OANDA: {oanda_count} | OKX: {okx_count}")
    log.info(f"Total live polling tasks to create: {total_tasks}")
    log.info("=" * 50)

    async with aiohttp.ClientSession() as session:
        # Run backfill in background (non-blocking for live polling)
        backfill_task = asyncio.create_task(backfill_historical(session))

        # Start price fetcher (updates market_prices independently)
        price_task = asyncio.create_task(fetch_all_prices(session))

        # Create live polling tasks immediately
        tasks = []
        for canonical, config in SYMBOL_CONFIG.items():
            for tf in TF_OANDA.keys():
                if config["oanda"]:
                    task = asyncio.create_task(
                        poll_oanda_symbol(session, canonical, config, tf),
                        name=f"OANDA:{canonical}:{tf}"
                    )
                    tasks.append(task)
                    log.info(f"[TASK CREATED] OANDA {canonical} {tf}")
                if config["okx"]:
                    task = asyncio.create_task(
                        poll_okx_symbol(session, canonical, config, tf),
                        name=f"OKX:{canonical}:{tf}"
                    )
                    tasks.append(task)
                    log.info(f"[TASK CREATED] OKX {canonical} {tf}")

        log.info(f"[WORKER CONFIG] Total live tasks: {len(tasks)}")
        log.info("Feeder running... (Ctrl+C to stop)")

        # Wait for backfill to complete (non-blocking for live tasks)
        await backfill_task

        # Keep all tasks running
        await asyncio.gather(*tasks, price_task)


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        log.info("Feeder stopped by user")
    except Exception as e:
        log.error(f"Feeder crashed: {e}")
        raise