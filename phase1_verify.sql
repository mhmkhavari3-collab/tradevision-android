-- ============================================================
-- PHASE 1: Verification Queries
-- ============================================================

-- 1. تعداد نمادها = 17
SELECT count(*) as symbol_count FROM symbol_mapping;

-- 2. لیست نمادها با منبع داده
SELECT canonical_symbol, oanda_instrument, okx_instrument, category 
FROM symbol_mapping ORDER BY category, canonical_symbol;

-- 3. بررسی PK و Index روی market_candles
SELECT 
  tc.table_name, 
  tc.constraint_name, 
  kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu 
  ON tc.constraint_name = kcu.constraint_name
WHERE tc.table_name = 'market_candles' AND tc.constraint_type = 'PRIMARY KEY';

-- 4. بررسی Index
SELECT indexname, indexdef 
FROM pg_indexes 
WHERE tablename = 'market_candles';

-- 5. بررسی Realtime فعال
SELECT schemaname, tablename, pubname
FROM pg_publication_tables
WHERE pubname = 'supabase_realtime' AND tablename IN ('market_candles', 'market_prices');

-- 6. بررسی RLS
SELECT tablename, policyname, permissive, roles, cmd, qual
FROM pg_policies
WHERE tablename IN ('symbol_mapping', 'market_candles', 'market_prices');

-- 7. بررسی ستون is_closed
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'market_candles' AND column_name = 'is_closed';