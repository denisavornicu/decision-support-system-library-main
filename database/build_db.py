"""Build librarie.db: load the Kaggle CSVs and fill in gaps (categories, demographics,
neighborhoods, promotions, events, allocation of sales to SKUs). Fully deterministic (seed fixed)."""
import sqlite3, os, calendar
import numpy as np, pandas as pd

HERE = os.path.dirname(os.path.abspath(__file__)); RAW = os.path.join(HERE, "..", "data", "raw")
DB = os.path.join(HERE, "librarie.db"); rng = np.random.default_rng(42)

TAXONOMY = {
 "Fiction": ["Sci‑Fi & Fantasy", "Mystery & Thriller", "Romance", "Contemporary Literature"],
 "Non-fiction": ["Biographies", "History", "Self-Help", "Business"],
 "Technical & Sciences": ["Computer Science", "Engineering", "Medicine", "Mathematics"],
 "Children & Young Adult": ["Illustrated Books", "Middle Grade", "Young Adult"],
 "Education": ["Textbooks", "Exam Preparation"]}
SEASON = {"Textbooks": {1: 1.5, 8: 2.0, 9: 2.0}, "Illustrated Books": {11: 1.4, 12: 1.9}, "Romance": {2: 1.8},
          "Self-Help": {1: 1.7}, "Sci‑Fi & Fantasy": {7: 1.3, 12: 1.2}, "Business": {1: 1.3, 9: 1.2},
          "Mystery & Thriller": {6: 1.2, 7: 1.3, 10: 1.2}, "Exam Preparation": {4: 1.4, 5: 1.5, 11: 1.2},
          "Young Adult": {6: 1.2, 12: 1.2}, "Middle Grade": {8: 1.3, 12: 1.3}}
STORES = [("YYC-DT", "Downtown", "Calgary Downtown"), ("YYC-NW", "Northwest", "Calgary NW"), ("YYC-SE", "Southeast", "Calgary SE"),
          ("ONLINE", "Online", "Online")]
NEIGH = [("Alberta", "Calgary Downtown", n) for n in ["Beltline", "Eau Claire", "East Village", "Mission"]] + \
        [("Alberta", "Calgary NW", n) for n in ["Kensington", "Brentwood", "Dalhousie", "Tuscany", "Varsity"]] + \
        [("Alberta", "Calgary SE", n) for n in ["Mahogany", "Auburn Bay", "Douglasdale", "McKenzie Towne"]] + \
        [("Alberta", "Online", "Edmonton"), ("Alberta", "Online", "Red Deer"), ("British Columbia", "Online", "Vancouver"),
         ("British Columbia", "Online", "Kelowna"), ("Saskatchewan", "Online", "Saskatoon"), ("Saskatchewan", "Online", "Regina"),
         ("Ontario", "Online", "Toronto"), ("Ontario", "Online", "Ottawa")]
EDU = ["High School", "College", "Bachelor's", "Master's", "Doctorate"]; AGEG = ["18-24", "25-34", "35-44", "45-54", "55+"]

def age_group(a): return AGEG[0] if a < 25 else AGEG[1] if a < 35 else AGEG[2] if a < 45 else AGEG[3] if a < 55 else AGEG[4]
def ym_range(a, b): return [d.strftime("%Y-%m") for d in pd.period_range(a, b, freq="M")]

def main():
    if os.path.exists(DB): os.remove(DB)
    con = sqlite3.connect(DB); con.executescript(open(os.path.join(HERE, "schema.sql"), encoding="utf-8").read())
    # --- dimensions
    con.executemany("INSERT INTO store VALUES (?,?,?)", STORES)
    subs = []; sid = 0
    for ci, (cat, lst) in enumerate(TAXONOMY.items(), 1):
        con.execute("INSERT INTO category VALUES (?,?)", (ci, cat))
        for s in lst: sid += 1; con.execute("INSERT INTO subcategory VALUES (?,?,?)", (sid, ci, s)); subs.append((sid, s))
    sub_names = [s for _, s in subs]
    # --- books (SKU from inventory)
    inv = pd.read_csv(os.path.join(RAW, "bookstore_inventory.csv")); inv["ym"] = inv["month"].str[:7]; inv = inv.rename(columns={"to": "store_id"})
    skus = sorted(inv.sku.unique()); perm = rng.permutation(len(skus))
    adj = ["Silent", "Hidden", "Last", "Golden", "Broken", "Northern", "Distant", "Crimson", "Quiet", "Endless"]
    noun = ["Orchard", "Horizon", "Code", "Letters", "Garden", "Harbor", "Algorithm", "Empire", "River", "Archive"]
    names = [f"The {a} {n}" for a in adj for n in noun]; rng.shuffle(names)
    authors = ["A. Whitfield", "M. Laurent", "J. Okafor", "S. Nakamura", "R. Delgado", "K. Johansson", "L. Haddad", "P. Novak", "T. Grant", "E. Moreau"]
    price = inv.groupby("sku").agg(c=("unit_cost", "median"), p=("unit_price", "median"))
    sku_sub = {}
    for i, k in enumerate(perm):
        s = skus[k]; sub = subs[i % len(subs)][0]; sku_sub[s] = sub
        con.execute("INSERT INTO book VALUES (?,?,?,?,?,?)", (s, names[i], authors[i % 10], sub, float(price.c[s]), float(price.p[s])))
    inv[["ym", "store_id", "sku", "qty", "unit_cost", "unit_price"]].to_sql("inventory_move", con, if_exists="append", index=False)
    # --- daily sales
    sd = pd.read_csv(os.path.join(RAW, "bookstore_sales.csv")); sd["ym"] = sd["date"].str[:7]
    sd.rename(columns={"gst_collected": "gst"})[["date", "ym", "year", "month", "store_id", "channel", "transactions", "revenue", "gst", "net_revenue"]] \
      .to_sql("sales_daily", con, if_exists="append", index=False)
    # --- promotions (subcategory discounts) and events
    promos = [("Self-Help January Sale", "Self-Help", "2022-01", 15), ("Back to School Textbooks", "Textbooks", "2022-09", 20),
              ("Holiday Illustrated Books", "Illustrated Books", "2022-12", 15), ("Summer Sci‑Fi & Fantasy", "Sci‑Fi & Fantasy", "2023-06", 15),
              ("Valentine's Romance", "Romance", "2023-02", 20), ("Computer Science -10%", "Computer Science", "2023-03", 10),
              ("Medicine -15%", "Medicine", "2023-10", 15), ("Autumn Biographies", "Biographies", "2023-09", 20),
              ("Exam Preparation May", "Exam Preparation", "2024-05", 15), ("Young Adult Summer", "Young Adult", "2024-07", 25),
              ("Business -10%", "Business", "2024-02", 10), ("History -20%", "History", "2024-10", 20),
              ("Black Friday 2022", None, "2022-11", 10), ("Black Friday 2023", None, "2023-11", 12), ("Black Friday 2024", None, "2024-11", 15)]
    name2id = {n: i for i, n in subs}; e_true = {i: rng.uniform(1.2, 3.2) for i, _ in subs}
    promo_rows = []
    for k, (nm, sub, ym, d) in enumerate(promos, 1):
        y, m = map(int, ym.split("-")); last = calendar.monthrange(y, m)[1]
        con.execute("INSERT INTO promotion VALUES (?,?,?,?,?,?)", (k, nm, name2id.get(sub), f"{ym}-01", f"{ym}-{last:02d}", d)); promo_rows.append((name2id.get(sub), ym, d))
    ev = [("Family Literacy Day", "Holiday", "01-27", "01-27"), ("Valentine's Day", "Holiday", "02-10", "02-14"),
          ("Calgary Stampede", "Festival", "07-05", "07-14"), ("Back to School", "Season", "08-20", "09-10"),
          ("Word on the Street Calgary", "Literary festival", "09-20", "09-26"), ("Black Friday", "Promotion", "11-24", "11-30"),
          ("Holiday Season", "Holiday", "12-10", "12-24"), ("Boxing Week", "Holiday", "12-26", "12-31")]
    k = 0
    for y in range(2019, 2026):
        for nm, tp, a, b in ev: k += 1; con.execute("INSERT INTO event VALUES (?,?,?,?,?)", (k, nm, tp, f"{y}-{a}", f"{y}-{b}"))
    con.execute("INSERT INTO event VALUES (?,?,?,?,?)", (k + 1, "Lockdown COVID-19", "Restrictions", "2020-03-17", "2020-06-30"))
    # --- fact_sales: net revenue per month x store x channel x SKU allocated by weights (inventory x season x promotion x noise)
    sub_of = lambda s: sub_names[sku_sub[s] - 1]
    promo_map = {}
    for sub, ym, d in promo_rows:
        for s_id in ([sub] if sub else [i for i, _ in subs]): promo_map[(s_id, ym)] = (d, e_true[s_id] if sub else 1.0)
    m_rev = sd.groupby(["ym", "store_id", "channel"]).net_revenue.sum().reset_index()
    invw = inv.groupby(["ym", "store_id", "sku"]).extended_retail.sum().reset_index()
    inv_idx = {k: g for k, g in invw.groupby(["ym", "store_id"])}
    rows = []
    for r in m_rev.itertuples():
        g = inv_idx.get((r.ym, r.store_id))
        if g is None: continue
        mth = int(r.ym[5:]); w = []
        for s, e in zip(g.sku, g.extended_retail):
            sid_ = sku_sub[s]; d, el = promo_map.get((sid_, r.ym), (0, 0))
            w.append(e * SEASON.get(sub_of(s), {}).get(mth, 1.0) * (1 + el * d / 100) * rng.lognormal(0, 0.12))
        w = np.array(w); w = w / w.sum() * r.net_revenue
        for s, rev in zip(g.sku, w):
            d = promo_map.get((sku_sub[s], r.ym), (0, 0))[0]
            p, c = float(price.p[s]) * (1 - d / 100), float(price.c[s]); u = rev / p
            rows.append((r.ym, int(r.ym[:4]), mth, r.store_id, r.channel, s, round(u, 2), round(rev, 2), round(u * c, 2)))
    con.executemany("INSERT INTO fact_sales VALUES (?,?,?,?,?,?,?,?,?)", rows)
    # --- customers: fill in gender, age, education, neighborhood + subcategory preferences
    c = pd.read_csv(os.path.join(RAW, "bookstore_customers_expanded.csv"))
    nb = {(r.region, r.name): i for i, r in enumerate(pd.DataFrame(NEIGH, columns=["province", "region", "name"]).itertuples(), 1)}
    for i, (p, rg, n) in enumerate(NEIGH, 1): con.execute("INSERT INTO neighborhood VALUES (?,?,?,?)", (i, p, rg, n))
    reg_of = {"YYC-DT": "Calgary Downtown", "YYC-NW": "Calgary NW", "YYC-SE": "Calgary SE", "ONLINE": "Online"}
    by_reg = {rg: [i for (r2, _), i in nb.items() if r2 == rg] for rg in reg_of.values()}
    mean_age = {"YYC-DT": 36, "YYC-NW": 45, "YYC-SE": 41, "ONLINE": 33}; sub_i = {n: i for i, n in enumerate(sub_names)}
    cust, prefs = [], []
    for r in c.itertuples():
        g = rng.choice(["F", "M", "Other"], p=[.52, .44, .04]); age = int(np.clip(rng.normal(mean_age[r.home_store], 13), 18, 82))
        edu = rng.choice(EDU, p=[.2, .2, .35, .18, .07]); w = np.ones(len(sub_names))
        def up(n, f): w[sub_i[n]] *= f
        if g == "F": up("Romance", 2.4); up("Self-Help", 1.5); up("Illustrated Books", 1.3)
        if g == "M": up("Sci‑Fi & Fantasy", 1.7); up("History", 1.6); up("Computer Science", 1.5); up("Engineering", 1.6)
        if age < 26: up("Textbooks", 2.5); up("Young Adult", 2.2); up("Exam Preparation", 2.0)
        if 28 <= age <= 45: up("Illustrated Books", 1.8); up("Middle Grade", 1.4); up("Business", 1.3)
        if age >= 50: up("Biographies", 2.3); up("History", 1.8); up("Mystery & Thriller", 1.6)
        if edu in ("Master's", "Doctorate"): up("Medicine", 2.0); up("Mathematics", 2.0); up("Computer Science", 1.5)
        if edu in ("High School", "College"): up("Mystery & Thriller", 1.4); up("Romance", 1.3)
        pick = rng.choice(len(sub_names), 3, replace=False, p=w / w.sum()); ts = float(r.total_spent)
        for j, share in zip(pick, (.55, .30, .15)): prefs.append((int(r.customer_id), subs[j][0], round(ts * share, 2)))
        esc = 1 if str(r.has_escalation) == "True" else 0
        cust.append((int(r.customer_id), r.signup_date, r.home_store, g, 2024 - age, age_group(age), edu, int(rng.choice(by_reg[reg_of[r.home_store]])),
                     int(r.annual_visits), float(r.online_ratio), ts, float(r.customer_rating), esc, r.escalation_reason if esc else None))
    con.executemany("INSERT INTO customer VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", cust)
    con.executemany("INSERT INTO customer_pref VALUES (?,?,?)", prefs); con.commit()
    q = lambda s: con.execute(s).fetchone()
    print("fact_sales:", q("SELECT COUNT(*) FROM fact_sales")[0], "| customers:", q("SELECT COUNT(*) FROM customer")[0], "| pref:", q("SELECT COUNT(*) FROM customer_pref")[0])
    print("net sales_daily vs fact:", round(q("SELECT SUM(net_revenue) FROM sales_daily")[0]), round(q("SELECT SUM(revenue) FROM fact_sales")[0]))
    con.close()

if __name__ == "__main__": main()
