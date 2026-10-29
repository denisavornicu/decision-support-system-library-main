-- Schema SQLite - SAD Library (Northern Lights Book Co., Calgary)
PRAGMA foreign_keys = ON;
DROP TABLE IF EXISTS customer_pref; DROP TABLE IF EXISTS fact_sales; DROP TABLE IF EXISTS sales_daily;
DROP TABLE IF EXISTS inventory_move; DROP TABLE IF EXISTS promotion; DROP TABLE IF EXISTS event;
DROP TABLE IF EXISTS customer; DROP TABLE IF EXISTS neighborhood; DROP TABLE IF EXISTS book;
DROP TABLE IF EXISTS subcategory; DROP TABLE IF EXISTS category; DROP TABLE IF EXISTS store;

CREATE TABLE store (store_id TEXT PRIMARY KEY, name TEXT NOT NULL, region TEXT NOT NULL);
CREATE TABLE category (category_id INTEGER PRIMARY KEY, name TEXT NOT NULL);
CREATE TABLE subcategory (subcategory_id INTEGER PRIMARY KEY, category_id INTEGER NOT NULL REFERENCES category, name TEXT NOT NULL);
CREATE TABLE book (sku TEXT PRIMARY KEY, title TEXT NOT NULL, author TEXT, subcategory_id INTEGER NOT NULL REFERENCES subcategory,
                   unit_cost REAL NOT NULL, unit_price REAL NOT NULL);
CREATE TABLE neighborhood (neighborhood_id INTEGER PRIMARY KEY, province TEXT NOT NULL, region TEXT NOT NULL, name TEXT NOT NULL);
CREATE TABLE customer (customer_id INTEGER PRIMARY KEY, signup_date TEXT, home_store TEXT REFERENCES store, gender TEXT, birth_year INTEGER,
                       age_group TEXT, education TEXT, neighborhood_id INTEGER REFERENCES neighborhood, annual_visits INTEGER,
                       online_ratio REAL, total_spent REAL, customer_rating REAL, has_escalation INTEGER, escalation_reason TEXT);
CREATE TABLE customer_pref (customer_id INTEGER REFERENCES customer, subcategory_id INTEGER REFERENCES subcategory, spend REAL,
                            PRIMARY KEY (customer_id, subcategory_id));
CREATE TABLE sales_daily (date TEXT, ym TEXT, year INTEGER, month INTEGER, store_id TEXT REFERENCES store, channel TEXT,
                          transactions INTEGER, revenue REAL, gst REAL, net_revenue REAL);
-- main fact table: net revenue/units/cost per month x store x channel x SKU (allocated from sales_daily; see build_db.py)
CREATE TABLE fact_sales (ym TEXT NOT NULL, year INTEGER, month INTEGER, store_id TEXT REFERENCES store, channel TEXT,
                         sku TEXT REFERENCES book, units REAL, revenue REAL, cost REAL);
CREATE TABLE inventory_move (ym TEXT, store_id TEXT, sku TEXT, qty INTEGER, unit_cost REAL, unit_price REAL);
CREATE TABLE promotion (promo_id INTEGER PRIMARY KEY, name TEXT, subcategory_id INTEGER REFERENCES subcategory, start_date TEXT, end_date TEXT, discount_pct REAL);
CREATE TABLE event (event_id INTEGER PRIMARY KEY, name TEXT, type TEXT, start_date TEXT, end_date TEXT);
CREATE INDEX ix_fact_ym ON fact_sales(ym); CREATE INDEX ix_fact_sku ON fact_sales(sku);
CREATE INDEX ix_fact_store ON fact_sales(store_id, channel);
CREATE INDEX ix_daily_date ON sales_daily(date); CREATE INDEX ix_daily_ym ON sales_daily(ym);
CREATE INDEX ix_cust_nb ON customer(neighborhood_id); CREATE INDEX ix_pref_sub ON customer_pref(subcategory_id);
