"""
OANDA Pricing Stream Manager - Phase 3.5

Receives raw ticks from OANDA Stream (1 connection).
Stores ticks in Price Cache for Worker to consume.

Architecture:
- Single connection for ALL OANDA instruments
- Auto-reconnect on disconnect
- No candle logic here (Worker handles candles)
- No DB writes here (Worker handles DB)
"""

import asyncio
import aiohttp
import json
import logging
from datetime import datetime, timezone
from typing import Dict, List

from price_cache import update_price, PriceTick

log = logging.getLogger("oanda_stream")

OANDA_ACCOUNT_ID = "001-001-21958739-001"
OANDA_STREAM_URL = f"https://stream-fxtrade.oanda.com/v3/accounts/{OANDA_ACCOUNT_ID}/pricing/stream"


class OandaStreamManager:
    """
    Manages OANDA Pricing Stream for real-time price ticks.
    
    Features:
    - Single connection for all instruments
    - Auto-reconnect on disconnect
    - Writes ticks to Price Cache
    - No candle logic, no DB writes
    """

    def __init__(self, instruments: List[str], token: str):
        """
        Args:
            instruments: OANDA instrument names (e.g. ["EUR_USD", "GBP_USD"])
            token: OANDA API token
        """
        self.instruments = instruments
        self.token = token
        self.running = False
        self._connection_count = 0

    async def start(self):
        """Start the OANDA Pricing Stream with auto-reconnect."""
        self.running = True
        
        while self.running:
            try:
                self._connection_count += 1
                log.info(f"[OANDA-STREAM] Connection #{self._connection_count}")
                log.info(f"[OANDA-STREAM] Instruments: {len(self.instruments)}")
                
                headers = {"Authorization": f"Bearer {self.token}"}
                params = {"instruments": ",".join(self.instruments)}
                
                async with aiohttp.ClientSession() as session:
                    async with session.get(
                        OANDA_STREAM_URL,
                        headers=headers,
                        params=params
                    ) as resp:
                        if resp.status != 200:
                            log.error(f"[OANDA-STREAM] HTTP {resp.status}")
                            await asyncio.sleep(5)
                            continue
                        
                        log.info(f"[OANDA-STREAM] Connected! Streaming...")
                        await self._listen(resp)
                        
            except asyncio.CancelledError:
                log.info("[OANDA-STREAM] Cancelled")
                break
            except Exception as e:
                log.error(f"[OANDA-STREAM] Error: {e}")
                if self.running:
                    log.info("[OANDA-STREAM] Reconnecting in 5s...")
                    await asyncio.sleep(5)

    async def _listen(self, resp: aiohttp.ClientResponse):
        """Listen for pricing events from OANDA stream."""
        buffer = ""
        tick_count = 0
        
        async for chunk in resp.content.iter_any():
            if not self.running:
                break
                
            buffer += chunk.decode("utf-8", errors="ignore")
            
            # OANDA streams JSON objects separated by newlines
            while "\n" in buffer:
                line, buffer = buffer.split("\n", 1)
                line = line.strip()
                if not line:
                    continue
                    
                try:
                    data = json.loads(line)
                    event_type = data.get("type", "")
                    
                    if event_type == "PRICE":
                        self._process_price(data)
                        tick_count += 1
                        if tick_count % 100 == 0:
                            log.info(f"[OANDA-STREAM] Processed {tick_count} ticks")
                    elif event_type == "HEARTBEAT":
                        pass  # Ignore heartbeats
                    elif event_type == "CONNECTION_CLOSE":
                        log.warning("[OANDA-STREAM] Connection closed by server")
                        return
                    else:
                        log.debug(f"[OANDA-STREAM] Event: {event_type}")
                        
                except json.JSONDecodeError:
                    log.warning(f"[OANDA-STREAM] Invalid JSON: {line[:100]}")

    def _process_price(self, data: dict):
        """Process a single price tick and write to cache."""
        instrument = data.get("instrument")
        if not instrument:
            return
        
        # Get bid/ask
        bids = data.get("bids", [])
        asks = data.get("asks", [])
        if not bids or not asks:
            return
        
        try:
            bid = float(bids[0].get("price", 0))
            ask = float(asks[0].get("price", 0))
            mid = (bid + ask) / 2
        except (ValueError, IndexError):
            return
        
        # Check tradeable
        tradeable = data.get("tradeable", True)
        
        # Create tick
        tick = PriceTick(
            canonical=instrument,  # Will be mapped by Worker
            bid=bid,
            ask=ask,
            mid=mid,
            timestamp=datetime.now(timezone.utc),
            tradeable=tradeable
        )
        
        # Write to cache
        update_price(tick)

    def stop(self):
        """Stop the stream manager."""
        self.running = False
