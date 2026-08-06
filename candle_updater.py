"""
Candle Updater - Phase 3.5

Reads prices from Price Cache and updates open candles.
Runs as a separate task alongside the existing Worker.

Architecture:
- Reads from Price Cache (written by OandaStreamManager)
- Updates current open candle OHLC
- Closes candle when timeframe ends
- Upserts to Supabase market_candles
- Updates market_prices directly from ticks
"""

import asyncio
import logging
from datetime import datetime, timezone, timedelta
from typing import Dict

import price_cache as pc
from price_cache import PriceTick

log = logging.getLogger("candle_updater")

# Track current open candles: {(canonical, tf): Candle}
_current_candles: Dict[tuple, dict] = {}

# Import SYMBOL_CONFIG, TF_OANDA, supabase from parent module
# These will be set by init_candle_updater()
SYMBOL_CONFIG = None
TF_OANDA = None
supabase = None
Candle = None


def init_candle_updater(symbol_config, tf_oanda, supabase_client, candle_class):
    """Initialize with references from main module."""
    global SYMBOL_CONFIG, TF_OANDA, supabase, Candle
    SYMBOL_CONFIG = symbol_config
    TF_OANDA = tf_oanda
    supabase = supabase_client
    Candle = candle_class


def _calc_open_time(now: datetime, tf: str) -> datetime:
    """Calculate candle open_time based on timeframe."""
    if tf == "1m":
        return now.replace(second=0, microsecond=0)
    elif tf == "5m":
        minute = (now.minute // 5) * 5
        return now.replace(minute=minute, second=0, microsecond=0)
    elif tf == "15m":
        minute = (now.minute // 15) * 15
        return now.replace(minute=minute, second=0, microsecond=0)
    elif tf == "30m":
        minute = (now.minute // 30) * 30
        return now.replace(minute=minute, second=0, microsecond=0)
    elif tf == "1h":
        return now.replace(minute=0, second=0, microsecond=0)
    elif tf == "4h":
        hour = (now.hour // 4) * 4
        return now.replace(hour=hour, minute=0, second=0, microsecond=0)
    elif tf == "1d":
        return now.replace(hour=0, minute=0, second=0, microsecond=0)
    elif tf == "1W":
        day = now - timedelta(days=now.weekday())
        return day.replace(hour=0, minute=0, second=0, microsecond=0)
    elif tf == "1M":
        return now.replace(day=1, hour=0, minute=0, microsecond=0)
    return now


async def update_candles_from_cache():
    """
    Read prices from Price Cache and update open candles.
    Runs as a continuous task.
    
    Flow:
    1. Read tick from cache
    2. Calculate candle open_time
    3. If new candle period -> close old candle, start new one
    4. Update current candle OHLC
    5. Upsert to DB
    """
    log.info("[CANDLE-UPDATER] Starting candle updater from Price Cache")
    
    while True:
        try:
            now = datetime.now(timezone.utc)
            
            # Get all OANDA symbols
            for canonical, config in SYMBOL_CONFIG.items():
                if not config.get("oanda"):
                    continue
                
                # Get price from cache (uses OANDA instrument as key)
                instrument = config["oanda"]
                tick = pc.get_price(instrument)
                if not tick:
                    continue
                
                # Update candles for each timeframe
                for tf in TF_OANDA.keys():
                    key = (canonical, tf)
                    open_time = _calc_open_time(now, tf)
                    
                    # Check if we have an existing candle
                    if key in _current_candles:
                        existing = _current_candles[key]
                        
                        if existing["open_time"] == open_time:
                            # Same candle period - update OHLC
                            existing["high"] = max(existing["high"], tick.mid)
                            existing["low"] = min(existing["low"], tick.mid)
                            existing["close"] = tick.mid
                            existing["volume"] += 0.001
                            
                            # Upsert to DB
                            await _upsert_candle(existing)
                            continue
                        else:
                            # New candle period - close old one
                            existing["is_closed"] = True
                            await _upsert_candle(existing)
                            log.info(f"[CANDLE-UPDATER] Closed {canonical}/{tf} @ {existing['open_time']}")
                    
                    # Create new candle
                    candle = {
                        "canonical_symbol": canonical,
                        "timeframe": tf,
                        "open_time": open_time,
                        "open": tick.mid,
                        "high": tick.mid,
                        "low": tick.mid,
                        "close": tick.mid,
                        "volume": 0.001,
                        "is_closed": False,
                        "source": "oanda_stream"
                    }
                    _current_candles[key] = candle
                    await _upsert_candle(candle)
                    
            # Update market_prices directly from ticks
            for canonical, config in SYMBOL_CONFIG.items():
                if not config.get("oanda"):
                    continue
                instrument = config["oanda"]
                tick = pc.get_price(instrument)
                if tick:
                    try:
                        supabase.table("market_prices").upsert({
                            "canonical_symbol": canonical,
                            "mid_price": tick.mid,
                            "bid_price": tick.bid,
                            "ask_price": tick.ask,
                            "updated_at": tick.timestamp.isoformat()
                        }, on_conflict="canonical_symbol").execute()
                    except Exception as e:
                        log.error(f"[CANDLE-UPDATER] Price upsert error {canonical}: {e}")
            
            # Sleep 100ms (10 updates per second)
            await asyncio.sleep(0.1)
            
        except Exception as e:
            log.error(f"[CANDLE-UPDATER] Error: {e}")
            await asyncio.sleep(1)


async def _upsert_candle(candle: dict):
    """Upsert candle to Supabase."""
    try:
        supabase.table("market_candles").upsert({
            "canonical_symbol": candle["canonical_symbol"],
            "timeframe": candle["timeframe"],
            "open_time": candle["open_time"].isoformat() if isinstance(candle["open_time"], datetime) else candle["open_time"],
            "open": candle["open"],
            "high": candle["high"],
            "low": candle["low"],
            "close": candle["close"],
            "volume": candle["volume"],
            "is_closed": candle["is_closed"],
            "source": candle["source"]
        }, on_conflict="canonical_symbol,timeframe,open_time").execute()
    except Exception as e:
        log.error(f"[CANDLE-UPDATER] Upsert error {candle['canonical_symbol']}/{candle['timeframe']}: {e}")
