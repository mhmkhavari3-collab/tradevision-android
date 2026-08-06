"""
Price Cache - Shared Memory for real-time price ticks.

OandaStreamManager writes ticks here.
Worker reads from here to update candles.

Thread-safe via asyncio (single event loop).
"""

from datetime import datetime, timezone
from typing import Dict, Optional, List
from dataclasses import dataclass


@dataclass
class PriceTick:
    """A single price tick from OANDA Stream."""
    canonical: str
    bid: float
    ask: float
    mid: float
    timestamp: datetime
    tradeable: bool = True


# Global price cache: {canonical: PriceTick}
_price_cache: Dict[str, PriceTick] = {}


def update_price(tick: PriceTick):
    """Update price cache with new tick."""
    _price_cache[tick.canonical] = tick


def get_price(canonical: str) -> Optional[PriceTick]:
    """Get latest price for a symbol."""
    return _price_cache.get(canonical)


def get_all_prices() -> Dict[str, PriceTick]:
    """Get all cached prices."""
    return _price_cache.copy()


def get_stale_symbols(max_age_seconds: float = 60) -> List[str]:
    """Get symbols with stale prices (older than max_age)."""
    now = datetime.now(timezone.utc)
    stale = []
    for canonical, tick in _price_cache.items():
        age = (now - tick.timestamp).total_seconds()
        if age > max_age_seconds:
            stale.append(canonical)
    return stale
