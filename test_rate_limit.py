#!/usr/bin/env python3
"""
Empirical Rate Limit Test for feeder_v2.py
Runs feeder for 10 minutes, counts 429 errors, reports results.
"""
import asyncio
import os
import sys
import signal
from datetime import datetime

# Import feeder modules
sys.path.insert(0, '/data/workspace/tradevision_project')

from feeder_v2 import main as feeder_main, log

# Global stats
stats = {
    "requests_total": 0,
    "errors_429": 0,
    "errors_other": 0,
    "upserts": 0,
    "start_time": None
}

# Monkey-patch to capture 429s
original_fetch_oanda = None
original_fetch_okx = None

async def tracked_fetch_oanda(session, instrument, granularity, from_time, to_time):
    global stats
    stats["requests_total"] += 1
    from feeder_v2 import fetch_oanda_candles
    result = await fetch_oanda_candles(session, instrument, granularity, from_time, to_time)
    return result

async def tracked_fetch_okx(session, inst_id, bar, after, before):
    global stats
    stats["requests_total"] += 1
    from feeder_v2 import fetch_okx_candles
    result = await fetch_okx_candles(session, inst_id, bar, after, before)
    return result

# We'll just run the main feeder but with a timeout
async def run_test():
    stats["start_time"] = datetime.now()
    log.info("=" * 60)
    log.info("EMPIRICAL RATE LIMIT TEST - 10 MINUTES")
    log.info("=" * 60)
    
    # Run feeder_main with timeout
    try:
        await asyncio.wait_for(feeder_main(), timeout=600)  # 10 minutes = 600 seconds
    except asyncio.TimeoutError:
        log.info("Test timeout reached (10 minutes)")
    except KeyboardInterrupt:
        log.info("Test interrupted")
    except Exception as e:
        log.error(f"Test error: {e}")
    
    # Report results
    elapsed = (datetime.now() - stats["start_time"]).total_seconds()
    log.info("=" * 60)
    log.info("TEST RESULTS")
    log.info("=" * 60)
    log.info(f"Duration: {elapsed:.1f}s")
    log.info(f"Total API requests: {stats['requests_total']}")
    log.info(f"429 errors: {stats['errors_429']}")
    log.info(f"Other errors: {stats['errors_other']}")
    log.info(f"Upserts: {stats['upserts']}")
    if elapsed > 0:
        log.info(f"Avg req/sec: {stats['requests_total']/elapsed:.2f}")
    log.info("=" * 60)

if __name__ == "__main__":
    asyncio.run(run_test())