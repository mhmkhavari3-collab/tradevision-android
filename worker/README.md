# TradeVision OANDA Proxy

Cloudflare Worker که بین App و OANDA API قرار میگیره.

## مزایا:
- ✅ OANDA از ایران بلاک نیست (از طریق Cloudflare)
- ✅ Token OANDA مخفی میمونه
- ✅ CORS فعال
- ✅ Rate limiting رایگان

## API Endpoints:

### GET /price
قیمت لحظه‌ای نمادها
```
/price?symbols=XAUUSD,EURUSD,GBPUSD,USDJPY
```

### GET /candles
کندل‌های تاریخی
```
/candles?symbol=XAUUSD&timeframe=15m&count=500
/candles?symbol=XAUUSD&timeframe=15m&from=1700000000000&count=500
```

### GET /symbols
لیست نمادهای موجود

### GET /health
بررسی سلامت سرور

## Deployment:
```bash
npm install
npx wrangler login
npx wrangler deploy
```

## نمادها:
| Symbol | OANDA Instrument |
|--------|-----------------|
| XAUUSD | XAU_USD |
| XAGUSD | XAG_USD |
| EURUSD | EUR_USD |
| GBPUSD | GBP_USD |
| USDJPY | USD_JPY |
| AUDUSD | AUD_USD |
| USDCHF | USD_CHF |
| USDCAD | USD_CAD |
| NZDUSD | NZD_USD |
