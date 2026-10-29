//AnalyticsService.java
package ro.uaic.sad;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Toata logica analitica (agregari SQL + estimari). Aplicatie de suport decizional: doar citire. */
@Service
public class AnalyticsService {
    private final JdbcTemplate db;
    public AnalyticsService(JdbcTemplate db) { this.db = db; }

    // ---------- helpers SQL ----------
    private static final String JOINS = " FROM fact_sales f JOIN book b ON b.sku=f.sku JOIN subcategory sc ON sc.subcategory_id=b.subcategory_id "
            + "JOIN category cat ON cat.category_id=sc.category_id JOIN seg ON seg.subcategory_id=b.subcategory_id ";
    // "seg" = ponderea segmentului de clienti (gen/varsta/studii) in cheltuiala fiecarei subcategorii; 1 daca nu e filtrat
    private static final String REV = "(f.revenue*seg.share)", UNITS = "(f.units*seg.share)", COST = "(f.cost*seg.share)";

    private List<Map<String, Object>> run(String select, Filters f, String from, String to, String extraWhere, String tail, Object... extra) {
        List<Object> args = new ArrayList<>();
        List<String> cond = new ArrayList<>();
        if (f.gender() != null) { cond.add("cu.gender=?"); args.add(f.gender()); }
        if (f.ageGroup() != null) { cond.add("cu.age_group=?"); args.add(f.ageGroup()); }
        if (f.education() != null) { cond.add("cu.education=?"); args.add(f.education()); }
        String cte = cond.isEmpty()
                ? "WITH seg AS (SELECT subcategory_id, 1.0 AS share FROM subcategory) "
                : "WITH seg AS (SELECT cp.subcategory_id, SUM(CASE WHEN " + String.join(" AND ", cond)
                  + " THEN cp.spend ELSE 0 END)*1.0/SUM(cp.spend) AS share FROM customer_pref cp JOIN customer cu ON cu.customer_id=cp.customer_id GROUP BY cp.subcategory_id) ";
        StringBuilder w = new StringBuilder(" WHERE f.ym BETWEEN ? AND ?");
        args.add(from); args.add(to);
        if (f.store() != null) { w.append(" AND f.store_id=?"); args.add(f.store()); }
        if (f.channel() != null) { w.append(" AND f.channel=?"); args.add(f.channel()); }
        if (f.category() != null) { w.append(" AND cat.category_id=?"); args.add(Integer.parseInt(f.category())); }
        args.addAll(Arrays.asList(extra));
        return db.queryForList(cte + "SELECT " + select + JOINS + w + " " + extraWhere + " " + tail, args.toArray());
    }

    private static double d(Object o) { return o == null ? 0 : ((Number) o).doubleValue(); }
    private static double r2(double v) { return Stats.round(v, 2); }
    public String maxYm() { return db.queryForObject("SELECT MAX(ym) FROM fact_sales", String.class); }
    public String minYm() { return db.queryForObject("SELECT MIN(ym) FROM fact_sales", String.class); }

    private Map<String, Double> monthly(Filters f, String from, String to, String expr) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (var r : run("f.ym AS ym, SUM(" + expr + ") AS v", f, from, to, "", "GROUP BY f.ym ORDER BY f.ym")) m.put((String) r.get("ym"), d(r.get("v")));
        return m;
    }

    private static List<String> months(String a, String b) {
        List<String> l = new ArrayList<>();
        for (String x = a; x.compareTo(b) <= 0; x = Filters.shift(x, 1)) l.add(x);
        return l;
    }

    static String status(double var, double good, double bad) { return var >= good ? "green" : var >= bad ? "yellow" : "red"; }

    // ---------- filtre (combo box-uri populate din SQL) ----------
    public Map<String, Object> filters() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stores", db.queryForList("SELECT store_id AS id, name FROM store WHERE store_id<>'ONLINE' ORDER BY store_id"));
        m.put("channels", db.queryForList("SELECT DISTINCT channel AS id FROM sales_daily ORDER BY 1"));
        m.put("categories", db.queryForList("SELECT category_id AS id, name FROM category ORDER BY name"));
        m.put("genders", db.queryForList("SELECT DISTINCT gender AS id FROM customer ORDER BY 1"));
        m.put("ageGroups", db.queryForList("SELECT DISTINCT age_group AS id FROM customer ORDER BY 1"));
        m.put("educations", db.queryForList("SELECT DISTINCT education AS id FROM customer ORDER BY 1"));
        m.put("minYm", minYm()); m.put("maxYm", maxYm());
        return m;
    }

    // ---------- KPI: perioada vs referinta ----------
    private Map<String, Object> kpis(Filters f, String from, String to) {
        var t = run("SUM(" + REV + ") AS rev, SUM(" + UNITS + ") AS units, SUM(" + COST + ") AS cost", f, from, to, "", "").get(0);
        List<Object> a = new ArrayList<>(List.of(from, to));
        String w = "ym BETWEEN ? AND ?";
        if (f.store() != null) { w += " AND store_id=?"; a.add(f.store()); }
        if (f.channel() != null) { w += " AND channel=?"; a.add(f.channel()); }
        var s = db.queryForMap("SELECT COALESCE(SUM(transactions),0) AS tx FROM sales_daily WHERE " + w, a.toArray());
        double rev = d(t.get("rev")), cost = d(t.get("cost")), tx = d(s.get("tx"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("revenue", r2(rev)); m.put("units", Math.round(d(t.get("units")))); m.put("margin", r2(rev - cost));
        m.put("marginPct", r2(rev == 0 ? 0 : (rev - cost) / rev * 100)); m.put("transactions", (long) tx);
        m.put("avgBasket", r2(tx == 0 ? 0 : rev / tx));
        return m;
    }

    public Map<String, Object> overview(Filters f) {
        var p = f.resolve(maxYm());
        var cur = kpis(f, p.from(), p.to()); var ref = kpis(f, p.refFrom(), p.refTo());
        Map<String, Object> var = new LinkedHashMap<>();
        for (String k : cur.keySet()) var.put(k, r2(Stats.pct(d(cur.get(k)), d(ref.get(k)))));
        return Map.of("period", p, "current", cur, "reference", ref, "variation", var, "timestamp", LocalDateTime.now().toString());
    }

    // ---------- trend + previziune ----------
    private double[] seasonalIndex(Filters f, String expr) {
        Map<String, Double> all = monthly(f, minYm(), maxYm(), expr);
        double[] sum = new double[12]; int[] cnt = new int[12]; double tot = 0; int n = 0;
        for (var e : all.entrySet()) {
            if (e.getKey().startsWith("2020")) continue; // excludem anul COVID din indicii de sezonalitate
            int mth = Integer.parseInt(e.getKey().substring(5)) - 1; sum[mth] += e.getValue(); cnt[mth]++; tot += e.getValue(); n++;
        }
        double avg = n == 0 ? 1 : tot / n; double[] idx = new double[12];
        for (int i = 0; i < 12; i++) idx[i] = cnt[i] == 0 || avg == 0 ? 1 : sum[i] / cnt[i] / avg;
        return idx;
    }

    /** Previziune: regresie liniara pe ultimele 36 luni deseasonalizate x indice de sezonalitate. */
    private List<Map<String, Object>> forecast(Filters f, int horizon) {
        String mx = maxYm(); double[] idx = seasonalIndex(f, REV);
        Map<String, Double> hist = monthly(f, Filters.shift(mx, -35), mx, REV);
        List<String> ms = months(Filters.shift(mx, -35), mx); double[] y = new double[ms.size()];
        for (int i = 0; i < y.length; i++) y[i] = hist.getOrDefault(ms.get(i), 0.0) / idx[Integer.parseInt(ms.get(i).substring(5)) - 1];
        double[] ab = Stats.regress(y); double[] res = new double[y.length];
        for (int i = 0; i < y.length; i++) res[i] = y[i] - (ab[0] + ab[1] * i);
        double sd = Stats.std(res); List<Map<String, Object>> out = new ArrayList<>();
        for (int k = 1; k <= horizon; k++) {
            String ym = Filters.shift(mx, k); double s = idx[Integer.parseInt(ym.substring(5)) - 1];
            double base = (ab[0] + ab[1] * (y.length - 1 + k)) * s;
            out.add(Map.of("ym", ym, "forecast", r2(base), "low", r2(Math.max(0, base - 1.28 * sd * s)), "high", r2(base + 1.28 * sd * s)));
        }
        return out;
    }

    public Map<String, Object> trend(Filters f, int horizon) {
        var p = f.resolve(maxYm()); Map<String, Double> cur = monthly(f, p.from(), p.to(), REV), ref = monthly(f, p.refFrom(), p.refTo(), REV);
        List<String> ms = months(p.from(), p.to()); double[] y = new double[ms.size()];
        for (int i = 0; i < y.length; i++) y[i] = cur.getOrDefault(ms.get(i), 0.0);
        double[] ma = Stats.movingAvg(y, 3), ab = Stats.regress(y); List<Map<String, Object>> pts = new ArrayList<>();
        List<String> rms = months(p.refFrom(), p.refTo());
        for (int i = 0; i < y.length; i++) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("ym", ms.get(i)); o.put("revenue", r2(y[i])); o.put("ma3", r2(ma[i])); o.put("trend", r2(ab[0] + ab[1] * i));
            o.put("ref", i < rms.size() ? r2(ref.getOrDefault(rms.get(i), 0.0)) : null); pts.add(o);
        }
        if (p.to().equals(maxYm())) for (var fc : forecast(f, horizon)) pts.add(new LinkedHashMap<>(fc));
        return Map.of("points", pts, "slopePerMonth", r2(ab[1]), "avg", r2(Stats.mean(y)));
    }

    // ---------- drill-down categorie -> subcategorie -> titlu ----------
    public Map<String, Object> drill(Filters f, String level, String parent, double good, double bad) {
        var p = f.resolve(maxYm());
        String id, name, extra = ""; Object[] args = new Object[0];
        switch (level) {
            case "subcategory" -> { id = "sc.subcategory_id"; name = "sc.name"; extra = "AND sc.category_id=?"; args = new Object[]{Integer.parseInt(parent)}; }
            case "title" -> { id = "b.sku"; name = "b.title"; extra = "AND b.subcategory_id=?"; args = new Object[]{Integer.parseInt(parent)}; }
            default -> { id = "cat.category_id"; name = "cat.name"; }
        }
        String sel = id + " AS id, " + name + " AS name, SUM(" + REV + ") AS rev, SUM(" + UNITS + ") AS units, SUM(" + REV + ")-SUM(" + COST + ") AS margin";
        var cur = run(sel, f, p.from(), p.to(), extra, "GROUP BY " + id + ", " + name, args);
        Map<String, Double> refRev = new HashMap<>();
        for (var r : run(sel, f, p.refFrom(), p.refTo(), extra, "GROUP BY " + id + ", " + name, args)) refRev.put(String.valueOf(r.get("id")), d(r.get("rev")));
        double total = cur.stream().mapToDouble(r -> d(r.get("rev"))).sum(); List<Map<String, Object>> rows = new ArrayList<>();
        for (var r : cur) {
            String key = String.valueOf(r.get("id")); double rev = d(r.get("rev")), rr = refRev.getOrDefault(key, 0.0), var = Stats.pct(rev, rr);
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", key); o.put("name", r.get("name")); o.put("revenue", r2(rev)); o.put("units", Math.round(d(r.get("units"))));
            o.put("marginPct", r2(rev == 0 ? 0 : d(r.get("margin")) / rev * 100)); o.put("sharePct", r2(total == 0 ? 0 : rev / total * 100));
            o.put("refRevenue", r2(rr)); o.put("varPct", r2(var)); o.put("status", status(var, good, bad)); o.put("hasChildren", !level.equals("title"));
            rows.add(o);
        }
        rows.sort((a, b) -> Double.compare(d(b.get("revenue")), d(a.get("revenue"))));
        return Map.of("level", level, "rows", rows, "total", r2(total));
    }

    // ---------- geografie clienti: provincie -> regiune -> cartier ----------
    public Map<String, Object> geo(Filters f, String level, String parent) {
        String grp = switch (level) { case "region" -> "n.region"; case "neighborhood" -> "n.name"; default -> "n.province"; };
        List<Object> a = new ArrayList<>(); StringBuilder w = new StringBuilder(" WHERE 1=1");
        if (level.equals("region")) { w.append(" AND n.province=?"); a.add(parent); }
        if (level.equals("neighborhood")) { w.append(" AND n.region=?"); a.add(parent); }
        if (f.store() != null) { w.append(" AND cu.home_store=?"); a.add(f.store()); }
        if (f.gender() != null) { w.append(" AND cu.gender=?"); a.add(f.gender()); }
        if (f.ageGroup() != null) { w.append(" AND cu.age_group=?"); a.add(f.ageGroup()); }
        if (f.education() != null) { w.append(" AND cu.education=?"); a.add(f.education()); }
        var rows = db.queryForList("SELECT " + grp + " AS name, COUNT(*) AS customers, SUM(cu.total_spent) AS spent, AVG(cu.total_spent) AS avgSpent, "
                + "AVG(cu.customer_rating) AS rating, AVG(cu.has_escalation)*100 AS escPct FROM customer cu JOIN neighborhood n ON n.neighborhood_id=cu.neighborhood_id"
                + w + " GROUP BY " + grp + " ORDER BY spent DESC", a.toArray());
        double avgEsc = rows.stream().mapToDouble(r -> d(r.get("escPct"))).average().orElse(0); List<Map<String, Object>> out = new ArrayList<>();
        for (var r : rows) {
            double e = d(r.get("escPct"));
            Map<String, Object> o = new LinkedHashMap<>(); o.put("id", r.get("name")); o.put("name", r.get("name")); o.put("customers", r.get("customers"));
            o.put("spent", r2(d(r.get("spent")))); o.put("avgSpent", r2(d(r.get("avgSpent")))); o.put("rating", r2(d(r.get("rating")))); o.put("escPct", r2(e));
            o.put("status", e <= avgEsc - 2 ? "green" : e <= avgEsc + 2 ? "yellow" : "red"); o.put("hasChildren", !level.equals("neighborhood")); out.add(o);
        }
        return Map.of("level", level, "rows", out);
    }

    // ---------- segmente clienti x categorii + asocieri (lift) ----------
    public Map<String, Object> segments(Filters f, String by) {
        String col = switch (by) { case "age" -> "cu.age_group"; case "education" -> "cu.education"; default -> "cu.gender"; };
        String w = f.store() != null ? " WHERE cu.home_store=?" : ""; Object[] a = f.store() != null ? new Object[]{f.store()} : new Object[0];
        var rows = db.queryForList("SELECT " + col + " AS seg, cat.name AS cat, sc.name AS sub, SUM(cp.spend) AS spend FROM customer_pref cp "
                + "JOIN customer cu ON cu.customer_id=cp.customer_id JOIN subcategory sc ON sc.subcategory_id=cp.subcategory_id "
                + "JOIN category cat ON cat.category_id=sc.category_id" + w + " GROUP BY 1,2,3", a);
        Map<String, Map<String, Double>> byCat = new TreeMap<>(); Map<String, Map<String, Double>> bySub = new TreeMap<>();
        Map<String, Double> subTot = new HashMap<>(), segTot = new HashMap<>(); double grand = 0;
        for (var r : rows) {
            String s = (String) r.get("seg"), c = (String) r.get("cat"), sb = (String) r.get("sub"); double v = d(r.get("spend"));
            byCat.computeIfAbsent(s, k -> new LinkedHashMap<>()).merge(c, v, Double::sum); bySub.computeIfAbsent(s, k -> new LinkedHashMap<>()).merge(sb, v, Double::sum);
            subTot.merge(sb, v, Double::sum); segTot.merge(s, v, Double::sum); grand += v;
        }
        List<Map<String, Object>> chart = new ArrayList<>(), lifts = new ArrayList<>();
        for (var e : byCat.entrySet()) { Map<String, Object> o = new LinkedHashMap<>(); o.put("segment", e.getKey()); e.getValue().forEach((k, v) -> o.put(k, r2(v))); chart.add(o); }
        for (var e : bySub.entrySet()) for (var s : e.getValue().entrySet()) {
            double share = s.getValue() / segTot.get(e.getKey()), overall = subTot.get(s.getKey()) / grand, lift = share / overall;
            Map<String, Object> o = new LinkedHashMap<>(); o.put("segment", e.getKey()); o.put("subcategory", s.getKey()); o.put("sharePct", r2(share * 100));
            o.put("overallPct", r2(overall * 100)); o.put("lift", r2(lift)); o.put("status", lift >= 1.3 ? "green" : lift >= 0.8 ? "yellow" : "red"); lifts.add(o);
        }
        lifts.sort((x, y) -> Double.compare(d(y.get("lift")), d(x.get("lift"))));
        return Map.of("chart", chart, "categories", db.queryForList("SELECT name FROM category ORDER BY name").stream().map(r -> r.get("name")).toList(), "lifts", lifts);
    }

    // ---------- varfuri / caderi, sezonalitate, evenimente ----------
    public Map<String, Object> peaks(Filters f) {
        var p = f.resolve(maxYm()); Map<String, Double> cur = monthly(f, p.from(), p.to(), REV); List<String> ms = months(p.from(), p.to());
        double[] y = ms.stream().mapToDouble(m -> cur.getOrDefault(m, 0.0)).toArray(); double mean = Stats.mean(y), sd = Stats.std(y);
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < y.length; i++) {
            String t = y[i] > mean + 0.5 * sd ? "Vârf" : y[i] < mean - 0.5 * sd ? "Cădere" : "Normal";
            list.add(Map.of("ym", ms.get(i), "revenue", r2(y[i]), "vsAvgPct", r2(Stats.pct(y[i], mean)), "type", t, "status", t.equals("Vârf") ? "green" : t.equals("Cădere") ? "red" : "yellow"));
        }
        double[] idx = seasonalIndex(f, REV); String[] nm = {"Ian", "Feb", "Mar", "Apr", "Mai", "Iun", "Iul", "Aug", "Sep", "Oct", "Nov", "Dec"}; List<Map<String, Object>> seas = new ArrayList<>();
        for (int i = 0; i < 12; i++) seas.add(Map.of("month", nm[i], "index", r2(idx[i]), "advice", idx[i] < 0.93 ? "Lună slabă: recomandat campanie promoțională" : idx[i] > 1.07 ? "Sezon de vârf: asigură stocul" : "Normal",
                "status", idx[i] < 0.93 ? "red" : idx[i] > 1.07 ? "green" : "yellow"));
        return Map.of("months", list, "seasonality", seas, "avg", r2(mean));
    }

    private double avgDaily(Filters f, String from, String to) {
        List<Object> a = new ArrayList<>(List.of(from, to)); String w = "date BETWEEN ? AND ?";
        if (f.store() != null) { w += " AND store_id=?"; a.add(f.store()); }
        if (f.channel() != null) { w += " AND channel=?"; a.add(f.channel()); }
        Double v = db.queryForObject("SELECT AVG(t) FROM (SELECT SUM(net_revenue) AS t FROM sales_daily WHERE " + w + " GROUP BY date)", Double.class, a.toArray());
        return v == null ? 0 : v;
    }

    public List<Map<String, Object>> events(Filters f) {
        String mx = maxYm() + "-31"; List<Map<String, Object>> out = new ArrayList<>();
        for (var e : db.queryForList("SELECT name, type, start_date AS s, end_date AS e FROM event WHERE end_date<=? ORDER BY start_date DESC", mx)) {
            String s = (String) e.get("s"), en = (String) e.get("e"); java.time.LocalDate ds = java.time.LocalDate.parse(s);
            double ev = avgDaily(f, s, en), base = avgDaily(f, ds.minusDays(35).toString(), ds.minusDays(8).toString()), lift = Stats.pct(ev, base);
            if (base == 0) continue;
            Map<String, Object> o = new LinkedHashMap<>(); o.put("name", e.get("name")); o.put("type", e.get("type")); o.put("start", s); o.put("end", en);
            o.put("avgDaily", r2(ev)); o.put("baselineDaily", r2(base)); o.put("liftPct", r2(lift)); o.put("status", lift >= 5 ? "green" : lift >= -5 ? "yellow" : "red"); out.add(o);
        }
        return out;
    }

    // ---------- promotii: elasticitate masurata + simulare ----------
    private double units(String from, String to, Integer sub) {
        String sql = "SELECT COALESCE(SUM(f.units),0) FROM fact_sales f JOIN book b ON b.sku=f.sku WHERE f.ym BETWEEN ? AND ?" + (sub != null ? " AND b.subcategory_id=?" : "");
        return sub != null ? db.queryForObject(sql, Double.class, from, to, sub) : db.queryForObject(sql, Double.class, from, to);
    }

    public List<Map<String, Object>> promoHistory() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var p : db.queryForList("SELECT p.name, p.subcategory_id AS sub, sc.name AS subName, p.start_date AS s, p.end_date AS e, p.discount_pct AS d FROM promotion p LEFT JOIN subcategory sc ON sc.subcategory_id=p.subcategory_id ORDER BY p.start_date")) {
            String a = ((String) p.get("s")).substring(0, 7), b = ((String) p.get("e")).substring(0, 7); Integer sub = p.get("sub") == null ? null : ((Number) p.get("sub")).intValue();
            double u = units(a, b, sub), ly = units(Filters.shift(a, -12), Filters.shift(b, -12), sub);
            double all = units(a, b, null) / Math.max(1, units(Filters.shift(a, -12), Filters.shift(b, -12), null)); // crestere generala (corectie)
            double lift = (u / Math.max(1, ly)) / all - 1, disc = d(p.get("d")) / 100, el = disc == 0 ? 0 : lift / disc;
            Map<String, Object> o = new LinkedHashMap<>(); o.put("name", p.get("name")); o.put("subcategory", p.get("subName") == null ? "Toate" : p.get("subName")); o.put("subId", sub);
            o.put("start", a); o.put("discountPct", d(p.get("d"))); o.put("liftPct", r2(lift * 100)); o.put("elasticity", r2(el));
            o.put("status", el >= 1.5 ? "green" : el >= 0.8 ? "yellow" : "red"); out.add(o);
        }
        return out;
    }

    public Map<String, Object> simulate(Filters f, String sub, double discountPct) {
        String mx = maxYm(); String from = Filters.shift(mx, -11); String extra = ""; Object[] a = new Object[0]; Integer sid = null;
        if (sub != null && !sub.equals("ALL")) { sid = Integer.parseInt(sub); extra = "AND b.subcategory_id=?"; a = new Object[]{sid}; }
        var t = run("SUM(" + REV + ") AS rev, SUM(" + UNITS + ") AS u, SUM(" + COST + ") AS c", f, from, mx, extra, "", a).get(0);
        double rev = d(t.get("rev")), u = d(t.get("u")), cost = d(t.get("c")), el = elasticityFor(sid);
        Map<String, Object> o = new LinkedHashMap<>(); o.put("elasticity", r2(el)); o.put("base", scenario(rev, u, cost, el, 0));
        o.put("sim", scenario(rev, u, cost, el, discountPct / 100)); List<Map<String, Object>> curve = new ArrayList<>();
        for (int x = 0; x <= 40; x += 5) { var s = scenario(rev, u, cost, el, x / 100.0); curve.add(Map.of("discount", x, "revenue", s.get("revenue"), "margin", s.get("margin"))); }
        o.put("curve", curve); double dm = d(((Map<?, ?>) o.get("sim")).get("margin")) - d(((Map<?, ?>) o.get("base")).get("margin"));
        o.put("verdict", dm >= 0 ? "Reducerea crește marja estimată: recomandată" : "Reducerea scade marja estimată: nerecomandată la acest nivel");
        o.put("status", dm >= 0 ? "green" : "red"); return o;
    }

    private Map<String, Object> scenario(double rev, double u, double cost, double el, double disc) {
        double uf = 1 + el * disc, rv = rev * uf * (1 - disc), cs = cost * uf;
        return Map.of("units", Math.round(u * uf), "revenue", r2(rv), "margin", r2(rv - cs), "marginPct", r2(rv == 0 ? 0 : (rv - cs) / rv * 100));
    }

    private double elasticityFor(Integer sub) {
        var h = promoHistory(); double own = h.stream().filter(x -> sub != null && sub.equals(x.get("subId"))).mapToDouble(x -> d(x.get("elasticity"))).average().orElse(Double.NaN);
        double all = h.stream().mapToDouble(x -> d(x.get("elasticity"))).average().orElse(1.0);
        return Math.min(6, Math.max(0.3, Double.isNaN(own) ? all : own));
    }

    // ---------- previziune de aprovizionare pe subcategorii ----------
    public Map<String, Object> supply(Filters f, int horizon) {
        String mx = maxYm(); var rows = run("sc.subcategory_id AS sid, sc.name AS name, f.ym AS ym, SUM(" + UNITS + ") AS u, SUM(" + COST + ") AS c", f, minYm(), mx, "", "GROUP BY 1,2,3");
        Map<String, Map<String, double[]>> m = new TreeMap<>(); for (var r : rows) m.computeIfAbsent((String) r.get("name"), k -> new HashMap<>()).put((String) r.get("ym"), new double[]{d(r.get("u")), d(r.get("c"))});
        List<Map<String, Object>> out = new ArrayList<>();
        for (var e : m.entrySet()) {
            Map<String, double[]> h = e.getValue(); double[] s = new double[12]; int[] c = new int[12]; double tot = 0; int n = 0;
            for (var x : h.entrySet()) { if (x.getKey().startsWith("2020")) continue; int mi = Integer.parseInt(x.getKey().substring(5)) - 1; s[mi] += x.getValue()[0]; c[mi]++; tot += x.getValue()[0]; n++; }
            double avg = n == 0 ? 1 : tot / n; double l12 = 0, p12 = 0, cost12 = 0;
            for (var x : h.entrySet()) { if (x.getKey().compareTo(Filters.shift(mx, -11)) >= 0) { l12 += x.getValue()[0]; cost12 += x.getValue()[1]; } else if (x.getKey().compareTo(Filters.shift(mx, -23)) >= 0) p12 += x.getValue()[0]; }
            double g = p12 == 0 ? 1 : Math.min(1.4, Math.max(0.7, l12 / p12)), base = l12 / 12, need = 0, lastYear = 0;
            for (int k = 1; k <= horizon; k++) {
                String ym = Filters.shift(mx, k); int mi = Integer.parseInt(ym.substring(5)) - 1; double idx = c[mi] == 0 ? 1 : s[mi] / c[mi] / avg;
                need += base * idx * Math.pow(g, k / 12.0); lastYear += h.getOrDefault(Filters.shift(ym, -12), new double[]{0, 0})[0];
            }
            double var = Stats.pct(need, lastYear), unitCost = l12 == 0 ? 0 : cost12 / l12;
            Map<String, Object> o = new LinkedHashMap<>(); o.put("name", e.getKey()); o.put("forecastUnits", Math.round(need)); o.put("orderUnits", Math.round(need * 1.10));
            o.put("orderValue", r2(need * 1.10 * unitCost)); o.put("lastYearUnits", Math.round(lastYear)); o.put("varPct", r2(var));
            o.put("status", var >= 10 ? "green" : var > -10 ? "yellow" : "red"); out.add(o);
        }
        out.sort((x, y) -> Double.compare(d(y.get("orderValue")), d(x.get("orderValue"))));
        return Map.of("rows", out, "safetyStockPct", 10, "forecast", forecast(f, horizon));
    }

    // ---------- decizie multi-criteriu ----------
    private record Agg(String id, String name, String cat, double rev, double prev, double cost, double r3, double p3) {}

    private List<Agg> aggregates(Filters f) {
        String mx = maxYm(); String s24 = Filters.shift(mx, -23), l12 = Filters.shift(mx, -11), l3 = Filters.shift(mx, -2);
        Map<String, double[]> m = new LinkedHashMap<>(); Map<String, String[]> names = new HashMap<>();
        for (var r : run("sc.subcategory_id AS sid, sc.name AS name, cat.name AS cat, f.ym AS ym, SUM(" + REV + ") AS rev, SUM(" + COST + ") AS cost", f, s24, mx, "", "GROUP BY 1,2,3,4")) {
            String id = String.valueOf(r.get("sid")), ym = (String) r.get("ym"); double rev = d(r.get("rev")), c = d(r.get("cost"));
            names.putIfAbsent(id, new String[]{(String) r.get("name"), (String) r.get("cat")}); double[] a = m.computeIfAbsent(id, k -> new double[5]);
            if (ym.compareTo(l12) >= 0) { a[0] += rev; a[2] += c; } else a[1] += rev;
            if (ym.compareTo(l3) >= 0) a[3] += rev;
            if (ym.compareTo(Filters.shift(l3, -12)) >= 0 && ym.compareTo(Filters.shift(mx, -12)) <= 0) a[4] += rev;
        }
        return m.entrySet().stream().map(e -> new Agg(e.getKey(), names.get(e.getKey())[0], names.get(e.getKey())[1], e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3], e.getValue()[4])).toList();
    }

    private static double[] norm(double[] v) {
        double mn = Arrays.stream(v).min().orElse(0), mxv = Arrays.stream(v).max().orElse(0); double[] o = new double[v.length];
        for (int i = 0; i < v.length; i++) o[i] = mxv == mn ? 50 : (v[i] - mn) / (mxv - mn) * 100; return o;
    }

    public Map<String, Object> decision(Filters f, double wg, double wm, double ws, double wmo) {
        List<Agg> ag = aggregates(f); int n = ag.size(); double total = ag.stream().mapToDouble(Agg::rev).sum();
        double[] g = new double[n], mg = new double[n], sh = new double[n], mo = new double[n];
        for (int i = 0; i < n; i++) { Agg a = ag.get(i); g[i] = Stats.pct(a.rev(), a.prev()); mg[i] = a.rev() == 0 ? 0 : (a.rev() - a.cost()) / a.rev() * 100; sh[i] = total == 0 ? 0 : a.rev() / total * 100; mo[i] = Stats.pct(a.r3(), a.p3()); }
        double[] ng = norm(g), nm = norm(mg), ns = norm(sh), nmo = norm(mo); double wt = Math.max(1e-9, wg + wm + ws + wmo);
        double medG = median(g), medM = median(mg); List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Agg a = ag.get(i); double score = (ng[i] * wg + nm[i] * wm + ns[i] * ws + nmo[i] * wmo) / wt;
            String action, why;
            if (g[i] < 0 && mg[i] < medM) { action = "Retragere / reducere stoc"; why = "Vânzări în scădere și marjă sub mediană"; }
            else if (g[i] >= medG && mg[i] >= medM) { action = "Extinde stocul și promovează"; why = "Creștere și marjă peste mediană"; }
            else if (mg[i] >= medM) { action = "Campanie de promovare"; why = "Marjă bună, dar creștere sub mediană"; }
            else { action = "Menține / negociază costuri"; why = "Creștere bună, marjă sub mediană"; }
            Map<String, Object> o = new LinkedHashMap<>(); o.put("id", a.id()); o.put("name", a.name()); o.put("category", a.cat()); o.put("revenue", r2(a.rev()));
            o.put("growthPct", r2(g[i])); o.put("marginPct", r2(mg[i])); o.put("sharePct", r2(sh[i])); o.put("momentumPct", r2(mo[i])); o.put("score", r2(score));
            o.put("status", score >= 60 ? "green" : score >= 40 ? "yellow" : "red"); o.put("action", action); o.put("rationale", why); out.add(o);
        }
        out.sort((x, y) -> Double.compare(d(y.get("score")), d(x.get("score"))));
        return Map.of("rows", out, "timestamp", LocalDateTime.now().toString());
    }

    private static double median(double[] v) { double[] s = v.clone(); Arrays.sort(s); return s.length == 0 ? 0 : s[s.length / 2]; }

    // ---------- alerte ----------
    public List<Map<String, Object>> alerts(Filters f) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Agg a : aggregates(f)) {
            double mo = Stats.pct(a.r3(), a.p3()), mg = a.rev() == 0 ? 0 : (a.rev() - a.cost()) / a.rev() * 100;
            if (mo <= -15) out.add(Map.of("level", "danger", "title", "Cădere vânzări: " + a.name(), "message", String.format(Locale.US, "Ultimele 3 luni: %.1f%% față de anul trecut.", mo)));
            else if (mo >= 15) out.add(Map.of("level", "success", "title", "Creștere: " + a.name(), "message", String.format(Locale.US, "Ultimele 3 luni: +%.1f%% față de anul trecut. Verifică stocul.", mo)));
            if (mg < 40) out.add(Map.of("level", "warning", "title", "Marjă scăzută: " + a.name(), "message", String.format(Locale.US, "Marja pe ultimele 12 luni este %.1f%%.", mg)));
        }
        for (var e : db.queryForList("SELECT name, start_date AS s FROM event WHERE start_date>? AND start_date<=? ORDER BY start_date LIMIT 3", maxYm() + "-31", Filters.shift(maxYm(), 3) + "-31"))
            out.add(Map.of("level", "info", "title", "Eveniment apropiat: " + e.get("name"), "message", "Începe la " + e.get("s") + ". Planifică stocul și promoțiile."));
        return out;
    }
}
