// TradeVision OANDA Proxy Worker
// App → Cloudflare Worker → OANDA API

const OANDA_TOKEN = '0dcfea75f50aa3b19d0e8c4810a865f4-e23a605e060d62d2d3e3011d1156f124';
const OANDA_BASE = 'https://api-fxtrade.oanda.com/v3/instruments';

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
  'Content-Type': 'application/json',
};

// Symbol mapping: our symbol → OANDA instrument
const SYMBOL_MAP = {
  'XAUUSD': 'XAU_USD',
  'XAGUSD': 'XAG_USD',
  'EURUSD': 'EUR_USD',
  'GBPUSD': 'GBP_USD',
  'USDJPY': 'USD_JPY',
  'AUDUSD': 'AUD_USD',
  'USDCHF': 'USD_CHF',
  'USDCAD': 'USD_CAD',
  'NZDUSD': 'NZD_USD',
  'EURGBP': 'EUR_GBP',
  'EURJPY': 'EUR_JPY',
  'GBPJPY': 'GBP_JPY',
  'WTICO_USD': 'WTICO_USD',
  'BCO_USD': 'BCO_USD',
};

// Timeframe mapping
const TF_MAP = {
  '1m': 'M1', '5m': 'M5', '15m': 'M15',
  '30m': 'M30', '1H': 'H1', '4H': 'H4',
  '1D': 'D', '1W': 'W', '1M': 'M',
};

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // Handle CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: CORS_HEADERS });
    }

    // Route: GET /price?symbols=XAUUSD,EURUSD,GBPUSD
    if (url.pathname === '/price') {
      return handlePrice(url);
    }

    // Route: GET /candles?symbol=XAUUSD&timeframe=15m&count=500
    // Route: GET /candles?symbol=XAUUSD&timeframe=15m&from=1700000000000
    if (url.pathname === '/candles') {
      return handleCandles(url);
    }

    // Route: GET /symbols — list available symbols
    if (url.pathname === '/symbols') {
      return new Response(JSON.stringify({
        symbols: Object.keys(SYMBOL_MAP).map(s => ({
          symbol: s,
          oanda: SYMBOL_MAP[s],
          name: s.replace('USD', '/USD').replace('_', ' '),
        })),
      }), { headers: CORS_HEADERS });
    }

    // Health check
    if (url.pathname === '/health') {
      return new Response(JSON.stringify({ status: 'ok', version: '1.0.0' }), { headers: CORS_HEADERS });
    }

    return new Response(JSON.stringify({ error: 'Not found', endpoints: ['/price', '/candles', '/symbols', '/health'] }), {
      status: 404,
      headers: CORS_HEADERS,
    });
  },
};

async function handlePrice(url) {
  const symbols = (url.searchParams.get('symbols') || 'XAUUSD,EURUSD,GBPUSD,USDJPY,AUDUSD,USDCHF').split(',');

  // Fetch latest candle for each symbol (count=1 gives us the current price)
  const results = await Promise.allSettled(
    symbols.map(async (sym) => {
      const oandaInst = SYMBOL_MAP[sym];
      if (!oandaInst) return { symbol: sym, error: 'Unknown symbol' };

      const apiUrl = `${OANDA_BASE}/${oandaInst}/candles?count=1&granularity=M1&price=BAM`;
      const resp = await fetch(apiUrl, {
        headers: { 'Authorization': `Bearer ${OANDA_TOKEN}` },
      });

      if (!resp.ok) throw new Error(`OANDA ${resp.status}`);

      const data = await resp.json();
      if (!data.candles || data.candles.length === 0) return { symbol: sym, error: 'No data' };

      const candle = data.candles[data.candles.length - 1];
      const mid = candle.mid || {};
      const bid = candle.bid || {};
      const ask = candle.ask || {};

      return {
        symbol: sym,
        oanda: oandaInst,
        bid: bid.c ? parseFloat(bid.c) : null,
        ask: ask.c ? parseFloat(ask.c) : null,
        mid: mid.c ? parseFloat(mid.c) : null,
        spread: bid.c && ask.c ? (parseFloat(ask.c) - parseFloat(bid.c)) : null,
        open: mid.o ? parseFloat(mid.o) : null,
        timestamp: candle.time,
      };
    })
  );

  const prices = results
    .filter(r => r.status === 'fulfilled')
    .map(r => r.value);

  return new Response(JSON.stringify({ prices, count: prices.length }), {
    headers: CORS_HEADERS,
  });
}

async function handleCandles(url) {
  const symbol = url.searchParams.get('symbol') || 'XAUUSD';
  const tf = url.searchParams.get('timeframe') || '15m';
  const count = parseInt(url.searchParams.get('count') || '500');
  const from = url.searchParams.get('from'); // Unix timestamp in milliseconds

  const oandaInst = SYMBOL_MAP[symbol];
  if (!oandaInst) {
    return new Response(JSON.stringify({ error: 'Unknown symbol', available: Object.keys(SYMBOL_MAP) }), {
      status: 400,
      headers: CORS_HEADERS,
    });
  }

  const oandaTf = TF_MAP[tf] || 'M15';
  let apiUrl = `${OANDA_BASE}/${oandaInst}/candles?granularity=${oandaTf}&price=M`;

  if (from) {
    // Load history: from timestamp + count
    apiUrl += `&from=${from}&count=${Math.min(count, 5000)}`;
  } else {
    // Initial load: most recent candles
    apiUrl += `&count=${Math.min(count, 5000)}`;
  }

  const resp = await fetch(apiUrl, {
    headers: { 'Authorization': `Bearer ${OANDA_TOKEN}` },
  });

  if (!resp.ok) {
    const err = await resp.text();
    return new Response(JSON.stringify({ error: `OANDA API error: ${resp.status}`, detail: err }), {
      status: 502,
      headers: CORS_HEADERS,
    });
  }

  const data = await resp.json();
  if (!data.candles) {
    return new Response(JSON.stringify({ candles: [], count: 0 }), { headers: CORS_HEADERS });
  }

  // Convert to standard format: time, open, high, low, close, volume
  const candles = data.candles.map(c => {
    const m = c.mid || c.bid || c.ask || {};
    return {
      time: Math.floor(new Date(c.time).getTime() / 1000),
      open: parseFloat(m.o),
      high: parseFloat(m.h),
      low: parseFloat(m.l),
      close: parseFloat(m.c),
      volume: c.volume || 0,
    };
  }).filter(c => !isNaN(c.open));

  // Deduplicate by time
  const seen = new Set();
  const unique = candles.filter(c => {
    if (seen.has(c.time)) return false;
    seen.add(c.time);
    return true;
  });

  return new Response(JSON.stringify({
    symbol,
    oanda: oandaInst,
    timeframe: tf,
    candles: unique,
    count: unique.length,
    hasMore: unique.length >= count,
  }), { headers: CORS_HEADERS });
}
