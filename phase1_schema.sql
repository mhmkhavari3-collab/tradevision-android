-- ============================================================
-- PHASE 1: Database Schema for TradeVision
-- ============================================================

-- 1.1 symbol_mapping (17 symbols)
CREATE TABLE symbol_mapping (
  canonical_symbol TEXT PRIMARY KEY,
  oanda_instrument TEXT,
  okx_instrument TEXT,
  display_name TEXT NOT NULL,
  category TEXT NOT NULL,
  precision INTEGER DEFAULT 2,
  min_move NUMERIC DEFAULT 0.01,
  is_active BOOLEAN DEFAULT true,
  created_at TIMESTAMPTZ DEFAULT now()
);

INSERT INTO symbol_mapping (canonical_symbol, oanda_instrument, okx_instrument, display_name, category, precision, min_move) VALUES
('XAUUSD', 'XAU_USD', NULL, 'XAU/USD', 'METALS', 2, 0.01),
('XAGUSD', 'XAG_USD', NULL, 'XAG/USD', 'METALS', 3, 0.001),
('WTI', 'WTICO_USD', NULL, 'WTI/USD', 'ENERGY', 3, 0.001),
('BRENT', 'BCO_USD', NULL, 'BRENT/USD', 'ENERGY', 3, 0.001),
('EURUSD', 'EUR_USD', NULL, 'EUR/USD', 'FOREX', 5, 0.00001),
('GBPUSD', 'GBP_USD', NULL, 'GBP/USD', 'FOREX', 5, 0.00001),
('USDJPY', 'USD_JPY', NULL, 'USD/JPY', 'FOREX', 3, 0.001),
('AUDUSD', 'AUD_USD', NULL, 'AUD/USD', 'FOREX', 5, 0.00001),
('USDCAD', 'USD_CAD', NULL, 'USD/CAD', 'FOREX', 5, 0.00001),
('NZDUSD', 'NZD_USD', NULL, 'NZD/USD', 'FOREX', 5, 0.00001),
('USDCHF', 'USD_CHF', NULL, 'USD/CHF', 'FOREX', 5, 0.00001),
('EURGBP', 'EUR_GBP', NULL, 'EUR/GBP', 'FOREX', 5, 0.00001),
('EURJPY', 'EUR_JPY', NULL, 'EUR/JPY', 'FOREX', 3, 0.001),
('GBPJPY', 'GBP_JPY', NULL, 'GBP/JPY', 'FOREX', 3, 0.001),
('BTC', NULL, 'BTC-USDT', 'BTC/USDT', 'CRYPTO', 2, 0.01),
('ETH', NULL, 'ETH-USDT', 'ETH/USDT', 'CRYPTO', 2, 0.01),
('SOL', NULL, 'SOL-USDT', 'SOL/USDT', 'CRYPTO', 2, 0.01);

-- 1.2 market_candles (مع is_closed)
CREATE TABLE market_candles (
  canonical_symbol TEXT NOT NULL REFERENCES symbol_mapping(canonical_symbol),
  timeframe TEXT NOT NULL,
  open_time TIMESTAMPTZ NOT NULL,
  open NUMERIC NOT NULL,
  high NUMERIC NOT NULL,
  low NUMERIC NOT NULL,
  close NUMERIC NOT NULL,
  volume NUMERIC DEFAULT 0,
  is_closed BOOLEAN DEFAULT false,
  source TEXT DEFAULT 'feeder',
  created_at TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (canonical_symbol, timeframe, open_time)
);

CREATE INDEX idx_market_candles_lookup ON market_candles (canonical_symbol, timeframe, open_time DESC);

-- 1.3 market_prices (Watchlist live prices)
CREATE TABLE market_prices (
  canonical_symbol TEXT PRIMARY KEY REFERENCES symbol_mapping(canonical_symbol),
  mid_price NUMERIC NOT NULL,
  bid_price NUMERIC,
  ask_price NUMERIC,
  prev_mid_price NUMERIC,
  change_percent NUMERIC,
  updated_at TIMESTAMPTZ DEFAULT now()
);

-- 1.4 Enable Realtime
ALTER PUBLICATION supabase_realtime ADD TABLE market_candles;
ALTER PUBLICATION supabase_realtime ADD TABLE market_prices;

-- 1.5 RLS Policies (anon read access)
ALTER TABLE symbol_mapping ENABLE ROW LEVEL SECURITY;
CREATE POLICY "anon_read_symbol_mapping" ON symbol_mapping FOR SELECT TO anon USING (true);
GRANT SELECT ON symbol_mapping TO anon;

ALTER TABLE market_candles ENABLE ROW LEVEL SECURITY;
CREATE POLICY "anon_read_market_candles" ON market_candles FOR SELECT TO anon USING (true);
GRANT SELECT ON market_candles TO anon;

ALTER TABLE market_prices ENABLE ROW LEVEL SECURITY;
CREATE POLICY "anon_read_market_prices" ON market_prices FOR SELECT TO anon USING (true);
GRANT SELECT ON market_prices TO anon;