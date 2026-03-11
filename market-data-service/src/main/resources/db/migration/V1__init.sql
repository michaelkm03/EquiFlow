-- Market Data Service Schema

CREATE TABLE ticker_prices (
    ticker VARCHAR(10) PRIMARY KEY,
    company_name VARCHAR(100),
    current_price NUMERIC(18, 4) NOT NULL,
    baseline_price NUMERIC(18, 4) NOT NULL,
    open_price NUMERIC(18, 4),
    high_price NUMERIC(18, 4),
    low_price NUMERIC(18, 4),
    volume BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE scenario_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scenario_name VARCHAR(100) NOT NULL,
    action VARCHAR(20) NOT NULL,
    description TEXT,
    triggered_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scenario_events_name ON scenario_events(scenario_name);
CREATE INDEX idx_ticker_prices_updated ON ticker_prices(updated_at);

-- Seed 30 tickers with baseline prices
INSERT INTO ticker_prices (ticker, company_name, current_price, baseline_price, open_price, high_price, low_price, volume) VALUES
    ('AAPL',  'Apple Inc.',                    189.50, 189.50, 188.00, 191.00, 187.50, 52000000),
    ('MSFT',  'Microsoft Corporation',          415.80, 415.80, 414.00, 417.50, 413.20, 22000000),
    ('GOOGL', 'Alphabet Inc.',                  175.30, 175.30, 174.00, 176.80, 173.50, 18000000),
    ('AMZN',  'Amazon.com Inc.',                195.40, 195.40, 194.00, 197.20, 193.80, 35000000),
    ('META',  'Meta Platforms Inc.',            520.60, 520.60, 518.00, 523.50, 517.30, 15000000),
    ('TSLA',  'Tesla Inc.',                     178.20, 178.20, 176.00, 181.00, 175.50, 85000000),
    ('NVDA',  'NVIDIA Corporation',             875.40, 875.40, 870.00, 882.00, 868.50, 42000000),
    ('BRK.B', 'Berkshire Hathaway Inc.',        395.70, 395.70, 394.00, 397.50, 393.20, 3500000),
    ('JPM',   'JPMorgan Chase & Co.',           205.30, 205.30, 204.00, 207.00, 203.50, 8500000),
    ('JNJ',   'Johnson & Johnson',              158.40, 158.40, 157.50, 159.80, 157.20, 6200000),
    ('V',     'Visa Inc.',                      278.90, 278.90, 277.00, 280.50, 276.80, 4800000),
    ('WMT',   'Walmart Inc.',                   196.20, 196.20, 195.00, 197.80, 194.80, 7300000),
    ('XOM',   'Exxon Mobil Corporation',        113.60, 113.60, 112.80, 114.50, 112.20, 14000000),
    ('MA',    'Mastercard Incorporated',        486.30, 486.30, 484.00, 488.50, 483.20, 3200000),
    ('PG',    'Procter & Gamble Co.',           165.70, 165.70, 164.80, 166.90, 164.50, 5900000),
    ('LLY',   'Eli Lilly and Company',          821.50, 821.50, 818.00, 826.00, 817.30, 2800000),
    ('HD',    'The Home Depot Inc.',            384.20, 384.20, 382.00, 386.50, 381.50, 3600000),
    ('ABBV',  'AbbVie Inc.',                    176.40, 176.40, 175.00, 178.00, 174.80, 4100000),
    ('BAC',   'Bank of America Corporation',     37.80,  37.80,  37.50,  38.20,  37.30, 32000000),
    ('KO',    'The Coca-Cola Company',           62.30,  62.30,  62.00,  62.80,  61.90, 11000000),
    ('CVX',   'Chevron Corporation',            158.90, 158.90, 157.80, 160.20, 157.50, 7800000),
    ('PEP',   'PepsiCo Inc.',                   172.60, 172.60, 171.50, 173.80, 171.20, 4200000),
    ('MRK',   'Merck & Co. Inc.',               125.40, 125.40, 124.80, 126.20, 124.50, 5600000),
    ('AVGO',  'Broadcom Inc.',                 1368.00,1368.00,1360.00,1375.00,1358.00, 1800000),
    ('COST',  'Costco Wholesale Corporation',   907.50, 907.50, 903.00, 912.00, 902.50, 2100000),
    ('MCD',   'McDonald Corporation',           294.30, 294.30, 292.00, 296.50, 291.80, 3100000),
    ('NFLX',  'Netflix Inc.',                   678.40, 678.40, 675.00, 682.00, 674.50, 4400000),
    ('AMD',   'Advanced Micro Devices Inc.',    167.80, 167.80, 165.00, 170.50, 164.80, 38000000),
    ('DIS',   'The Walt Disney Company',         89.60,  89.60,  88.80,  90.50,  88.30, 9800000),
    ('PYPL',  'PayPal Holdings Inc.',            62.70,  62.70,  62.00,  63.50,  61.80, 12000000);
